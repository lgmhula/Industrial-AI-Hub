# Phase 4 AI 模块集成 Runbook（SETUP 级 step-by-step）

> **目标**：在已 `docker compose up -d` 跑起来 + admin 登录可用的基线上，**无需猜**，严格按本文件 6 步把 Phase 4 AI 模块从「默认关闭」一路验证到「AI 巡检 → 自动报警 → SSE 推送 → 限流回滚」全链路打通。
> **适用版本**：Baseline v2.3.0 + Phase 4 Day 66-89（DeepSeek + Spring AI + Function Calling + RAG + Agent + MCP + MQ + SSE + 自动报警 + 限流/兜底统一）
> **关联文档**：[SETUP.md](../SETUP.md)（环境起 + 登录）｜[Application-Architecture.md §2a](../Architecture/Application-Architecture.md)（巡检推送链路 ASCII 图，Day 88 交付）｜[AGENTS.md §3](../../AGENTS.md)（当前状态）
> **一句话版**：`.env` 开 `DEEPSEEK_ENABLED=true` → 重启 → `curl POST /api/rag/documents` 入 PDF → `curl POST /api/ai/agents/inspection-report` 触发巡检 → 浏览器 `/inspection` 看 SSE → curl 3 次 `/api/ai/chat` 看 429。

---

## 0. 前置条件

| 项 | 要求 | 验证命令 |
|----|------|----------|
| 基础设施 | MySQL / Redis / RabbitMQ / backend 四容器 `Up`（见 [SETUP §3](../SETUP.md#3-启动后端)） | `docker compose ps` |
| 后端健康 | `/actuator/health` = `UP` | `curl -s http://localhost:8080/actuator/health` |
| admin 登录 | 已 `POST /api/auth/login` 拿到 `accessToken`（用户名 `admin` / 密码 `admin123`） | 见 [SETUP §5](../SETUP.md#5-登录验证) |
| 前端（可选） | `npm run dev` 在 5173 端口（Step 4 SSE 验证需要） | `curl -sI http://localhost:5173` |
| DeepSeek 配额 | 账户有 ≥1$ 余额（或免费额度未耗尽），否则 Step 1 起一路 429 | DeepSeek 控制台 |

> 下文 `$TOKEN` = admin 登录返回的 `data.accessToken`，统一放进 `Authorization: Bearer $TOKEN`。
> bash 下用 `export TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r .data.accessToken)` 一次到位。

---

## 1. Step 1 — 启用 DeepSeek + MCP 通道

### 1.1 编辑 `.env`（项目根目录，ADR 0015 SSOT）

在 `.env` 末尾追加 / 取消注释（**真实 Key 禁止提交 Git**，`.env` 已被 `.gitignore` 排除）：

```dotenv
# DeepSeek（必须）
DEEPSEEK_ENABLED=true                          # 关键：默认 false，改成 true 才会启用
DEEPSEEK_API_KEY=sk-你的真实key                  # DeepSeek 控制台 https://platform.deepseek.com 申请
# 以下为默认值，按需调整
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat                   # 摘要/诊断/chat 用 chat；如要 reasoner 单独改
DEEPSEEK_TIMEOUT_SECONDS=30
DEEPSEEK_MAX_TOKENS=1024

# MCP（Step 3 巡检必需，ADR 0029）
MCP_ACCESS_TOKEN=                              # 留空 = 内网可信直连；公网部署必须 `openssl rand -base64 48` 生成强随机值
MCP_CLIENT_BASE_URL=http://localhost:8080      # 进程内联调无需改
MCP_CLIENT_SSE_ENDPOINT=/mcp/sse
```

> **fail-fast 约束（AGENTS §8.3）**：`DEEPSEEK_API_KEY` 禁止用 `${VAR:}` 空默认。`DEEPSEEK_ENABLED=true` 但缺 Key → 启动直接失败，不会静默退化。

### 1.2 重启后端

```bash
# 路径 A：容器后端
docker compose restart backend

# 路径 B：宿主机后端
cd backend && ./mvnw spring-boot:run
```

### 1.3 验证 AI 文本补全

```bash
curl -s -X POST http://localhost:8080/api/ai/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"用一句话介绍工业设备预测性维护"}' | jq
```

**期望响应**（关键字段）：

```json
{
  "code": 200,
  "data": {
    "answer": "预测性维护通过传感器数据...",
    "model": "deepseek-chat",
    "promptTokens": 12,
    "completionTokens": 38,
    "totalTokens": 50
  }
}
```

| 现象 | 原因 | 解法 |
|------|------|------|
| `code=503` message 含「未启用/未配置/第三方」 | `DEEPSEEK_ENABLED` 还是 false，或 `.env` 改完没重启 | `grep DEEPSEEK_ENABLED .env` 确认；`docker compose restart backend` |
| `code=503` message 含「DeepSeek 调用失败」 | Key 错误 / 网络不通 / 配额耗尽 | curl DeepSeek 原始接口排查；DeepSeek 控制台看配额 |
| 启动日志 `Could not resolve placeholder 'DEEPSEEK_API_KEY'` | `.env` 工作目录不对，或 OS 环境变量覆盖了空值 | 工作目录需 `backend/` 或项目根（dev profile 双候选 `../.env` / `./.env` 自动定位）；清掉 IDEA Run Configuration 残留 env |

---

## 2. Step 2 — RAG 知识库入库 + 问答验证

### 2.1 上传 PDF 设备手册（ADMIN）

```bash
# 准备一份 PDF（任意设备手册，<10MB；PDFBox 3.0.8 解析）
curl -s -X POST http://localhost:8080/api/rag/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@/path/to/device-manual.pdf' | jq
```

**期望响应**：

```json
{
  "code": 200,
  "data": {
    "fileName": "device-manual.pdf",
    "chunkCount": 42,
    "embedded": true
  }
}
```

> **端点纠正**：Day 89 笔记 §5 写的 `/api/rag/ingest/upload` 是笔误，实际端点是 [POST /api/rag/documents](../../backend/src/main/java/dev/reboot/controller/RagController.java#L45)（`consumes=multipart/form-data`，ADMIN 权限）。

### 2.2 验证 knowledge_chunk 入库（DB 侧）

```bash
docker compose exec mysql mysql -uroot -p$MYSQL_PASSWORD reboot \
  -e "SELECT id, doc_id, chunk_index, LEFT(content, 60) AS preview, CHAR_LENGTH(content) AS len FROM knowledge_chunk ORDER BY id DESC LIMIT 5;"
```

期望：≥1 行，`len` 在 200-1000 之间（TextChunker 默认 300 字符 + 重叠）。

### 2.3 RAG 问答验证（带引文）

```bash
curl -s -X POST http://localhost:8080/api/rag/ask \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"该设备的日常巡检项目有哪些？"}' | jq
```

**期望响应**（`citations` 必须非空，`similarity` > 0.3）：

```json
{
  "code": 200,
  "data": {
    "answer": "根据手册，日常巡检包括：1. 电源指示灯... 2. 温度...",
    "citations": [
      { "chunkIndex": 5, "similarity": 0.71, "content": "日常巡检：电源指示灯..." },
      { "chunkIndex": 12, "similarity": 0.65, "content": "..." }
    ]
  }
}
```

| 现象 | 原因 | 解法 |
|------|------|------|
| `citations: []` 且 answer 含「知识库中缺少相关信息」 | 入库失败 / 问题与手册无关 | 回 2.2 看 DB；或换一个明显在手册里的问题 |
| `chunkCount: 0` | PDF 是扫描件（无文本层）/ 加密 PDF | PDFBox 不做 OCR；用 `pdftotext` 预检 PDF 是否含文本 |
| `code=503` | DeepSeek 未启用（Step 1 没做完） | 回 Step 1 |

---

## 3. Step 3 — AI 巡检 + 业务闭环（alarm 落库）

### 3.1 触发巡检日报（ADMIN）

```bash
curl -s -X POST http://localhost:8080/api/ai/agents/inspection-report \
  -H "Authorization: Bearer $TOKEN" | jq
```

**期望响应**（关键字段）：

```json
{
  "code": 200,
  "data": {
    "reportDate": "2026-09-03",
    "summary": "今日巡检 3 台设备...",
    "deviceCount": 3,
    "alarmCount": 1,
    "toolRounds": 3,
    "toolCalls": 7,
    "truncated": false,
    "detectedIssues": [
      { "deviceId": 5, "deviceCode": "PUMP-005", "alarmType": "TEMP_HIGH", "severity": 2, "description": "温度连续 3 次超阈值" }
    ],
    "autoAlarmCount": 1
  }
}
```

> **链路**（ADR 0030 + ADR 0031 + Day 86）：`McpInspectionAgentService.generate()` → Agent 通过 MCP 工具列设备/查数据/查告警 → `AiAlarmAutoCreator.autoCreateAlarms()` 把 detectedIssues 落 alarm 表（幂等）→ `InspectionReportProducer` 投递到 `inspection.exchange` MQ → Consumer 消费 → PushGateway 路由到 SSE。
> 完整 ASCII 图见 [Application-Architecture.md §2a](../Architecture/Application-Architecture.md)。

### 3.2 验证 alarm 表新增 AUTO_ALARM 记录

```bash
docker compose exec mysql mysql -uroot -p$MYSQL_PASSWORD reboot \
  -e "SELECT id, device_id, alarm_type, severity, triggered_at FROM alarm WHERE source='AI' OR alarm_type LIKE 'AI_%' ORDER BY id DESC LIMIT 5;"
```

期望：≥1 行，`triggered_at` 是刚才触发时间。

### 3.3 幂等验证（再调一次，alarm 不应翻倍）

```bash
# 第二次调用同一巡检
curl -s -X POST http://localhost:8080/api/ai/agents/inspection-report \
  -H "Authorization: Bearer $TOKEN" | jq '.data.autoAlarmCount'
# 期望：与第一次相同（不是 2 倍）—— Redis SETNX 命中
```

**幂等键**（[AiAlarmAutoCreator.java](../../backend/src/main/java/dev/reboot/service/AiAlarmAutoCreator.java) §65-67）：

```
ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}   TTL 24h   Redis SETNX
```

- 命中 → 跳过；Redis 不可用（null / 抛异常）→ **降级不做去重**（warn 日志，不阻塞主流程），见 §6 故障 3。

| 现象 | 原因 | 解法 |
|------|------|------|
| `code=503` message 含「MCP 客户端连接失败」 | MCP Server 未起 / `MCP_ACCESS_TOKEN` 不一致 | 看 Step 1.1 的 MCP 三变量；后端日志 grep `McpInspectionSession` |
| `toolRounds: 6` `truncated: true` | 设备过多触发 6 轮硬限（ADR 0026） | 设备数 ≥30 时正常现象；想完整覆盖分站点分批跑 |
| 第二次 `autoAlarmCount` 翻倍 | Redis 未连（降级去重） → 又命中 | 回 §6 故障 3 修 Redis |

---

## 4. Step 4 — SSE 推送验证（浏览器 + curl）

### 4.1 浏览器侧（推荐）

1. 打开 `http://localhost:5173`，用 admin 登录
2. 侧边栏点「巡检日报」（路由 `/inspection`，[InspectionReport.vue](../../frontend/src/views/InspectionReport.vue)）
3. 顶部连接状态显示绿色「已连接」
4. 后台 `curl POST /api/ai/agents/inspection-report` 触发一次
5. 页面应在 1-2 秒内出现新日报卡片，含「AI 自动报警」红色徽章

### 4.2 命令行侧（无前端时）

`curl` 不会处理 SSE 长连，用 `--no-buffer` 看原始事件流：

```bash
# SSE 端点支持 ?token= query fallback（仅 /api/push/** 端点，REST 端点不读 query 防 token 泄漏）
curl -N --no-buffer "http://localhost:8080/api/push/inspection?token=$TOKEN" &
SSE_PID=$!

# 触发一次巡检
curl -s -X POST http://localhost:8080/api/ai/agents/inspection-report \
  -H "Authorization: Bearer $TOKEN" > /dev/null

# 观察 curl 输出应出现：
# event: inspection-report
# data: {"reportDate":"2026-09-03", ...}

kill $SSE_PID
```

> **JWT 解析路径**（[JwtAuthFilter.java](../../backend/src/main/java/dev/reboot/security/JwtAuthFilter.java#L136)）：Authorization header 优先；缺则从 `?token=` query 读（仅 `/api/push/` 前缀）。**REST 端点不读 query**，防止 token 通过 URL 泄漏到 nginx access_log。

### 4.3 连接级断言

| 检查点 | 期望 |
|--------|------|
| HTTP 状态 | 200，`Content-Type: text/event-stream` |
| 连接保持时长 | 30min（[SseEmitterRegistry](../../backend/src/main/java/dev/reboot/mq/SseEmitterRegistry.java) 默认 timeout） |
| 具名事件 | `event: inspection-report`（不是 `message`），前端 `addEventListener('inspection-report', ...)` 监听 |
| 自动重连 | 浏览器 `EventSource` 原生指数退避；`onerror` 时 `readyState=2(CLOSED)` 手动 3s 重试 |
| 断线清理 | `onCompletion` / `onTimeout` / `onError` 三回调自动 `registry.remove()` 防泄漏 |

### 4.4 nginx 反代注意（生产部署必看）

如果前端不是直连 8080，而是走 nginx 反代，必须为 `/api/push/` 关闭缓冲：

```nginx
location /api/push/ {
    proxy_pass http://backend:8080;
    proxy_buffering off;          # 关键：不开会导致 SSE 事件被缓冲到连接关闭才一次性吐
    proxy_cache off;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_read_timeout 3600s;     # 长连保留 1h
    access_log off;               # 防 token 通过 query 写入访问日志
}
```

---

## 5. Step 5 — 限流验证 + 运维参数

### 5.1 配置参数（Day 89，[AiRateLimitInterceptor.java](../../backend/src/main/java/dev/reboot/security/AiRateLimitInterceptor.java)）

> **遗留**：当前 `rate.limit.ai.*` **未写入 application.yml**，靠代码默认值（Day89.md §4.5）。如需覆盖，在 `application-dev.yml` / `.env` 加：

```yaml
rate:
  limit:
    ai:
      permits: 2              # VIEWER/OPERATOR 每秒令牌数
      adminPermits: 5         # ADMIN 每秒令牌数
      acquireTimeoutMs: 150   # 拿不到令牌的最长等待（ms），超时即 429
```

### 5.2 拦截顺序（[WebMvcConfig](../../backend/src/main/java/dev/reboot/config/WebMvcConfig.java)）

```
order -1   AiRateLimitInterceptor     /api/ai/** /api/agents/** /api/rag/** /api/mcp/**
order  0   RateLimitInterceptor（通用） /api/** 除 /api/auth/**
order  1   AuthInterceptor（@RequireRole）/api/** 除 /api/auth/**
```

> **AI 限流先于 Auth 的原因**：匿名打 `/api/ai/chat`（未登录本应 503）如果不先限流，攻击者可以 1k QPS 打"503 AI 未启用"，DeepSeek 不花钱但 CPU 被打满。

### 5.3 限流桶粒度

| 用户类型 | 桶 key | 默认速率 |
|----------|--------|----------|
| 已登录（JWT userId 存在） | `u:{userId}` 每用户独立桶 | 2 req/s |
| ADMIN | 同 `u:{userId}` 但速率放宽 | 5 req/s |
| 匿名未登录 | `ip:{clientIp}`（X-Forwarded-For 链取第一个真实 IP） | 2 req/s |

> **热更新**：用户升级 ADMIN 后，下次请求检测到 `limiter.getRate() != rate` 自动 `setRate()`，不重启即生效。

### 5.4 验证 429（普通用户）

```bash
# 用 viewer 账号登录（或新建一个非 admin）
export VTOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"viewer","password":"viewer123"}' | jq -r .data.accessToken)

# 连续打 3 次（viewer 默认 2/s，第 3 发必 429）
for i in 1 2 3; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/ai/chat \
    -H "Authorization: Bearer $VTOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"prompt":"hi"}'
done
# 期望：200 / 200 / 429
```

### 5.5 验证 ADMIN 放宽（5/s 不触发）

```bash
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/ai/chat \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"prompt":"hi"}'
done
# 期望：6 个 200（5/s 桶 + 150ms timeout 足够 6 发连续过）
```

### 5.6 429 响应体格式

```json
{ "code": 429, "message": "AI 接口调用过于频繁，请稍后重试", "data": null }
```

> 与通用 `RateLimitInterceptor` body 结构一致但 message 明说「AI 接口」，前端可区分错误来源。

---

## 6. Step 6 — 常见故障速查 + 回滚策略

### 6.1 故障速查表

| # | 现象 | 根因 | 定位 | 回滚 / 解法 |
|---|------|------|------|--------------|
| 1 | `/api/ai/*` 全部 503 message 含「未启用/未配置/第三方」 | DeepSeek 未启用 / Key 缺失 | `grep DEEPSEEK .env`；后端日志 `DeepSeek enabled=false` | `.env` 设 `DEEPSEEK_ENABLED=true` + 真实 Key + 重启 |
| 2 | `/api/ai/*` 503 message 含「DeepSeek 调用失败」 | Key 错 / 配额耗尽 / DeepSeek 限流 429 | 直接 curl DeepSeek 原始接口；DeepSeek 控制台看配额 | 换 Key / 充值 / 等配额恢复；应急可临时 `DEEPSEEK_ENABLED=false` 回到「AI 关闭」基线 |
| 3 | 巡检第二次 `autoAlarmCount` 翻倍 / alarm 表出现重复 | Redis 未连 → `AiAlarmAutoCreator` 降级「不做去重」（warn 日志） | `docker compose ps redis`；后端日志 `AI 自动报警幂等 Redis SETNX 异常，降级不做去重` | 修 Redis 连接（`REDIS_PASSWORD` 对不对、容器是否 Up）；清理 alarm 表重复行（`DELETE FROM alarm WHERE id IN (...)`）；重新跑巡检 |
| 4 | SSE 端点 403「无可访问站点，无法订阅巡检日报」 | 非 ADMIN 用户且 `SiteAccessService.accessibleSiteIds(userId)` 返回空 List | 检查 `user_site` 表是否给该用户分配站点 | `INSERT INTO user_site ...` 给站点访问权；或用 admin 登录 |
| 5 | SSE 连上但收不到事件 | InspectionReportConsumer 没消费 / PushGateway 路由不到 | RabbitMQ 管理台 `http://localhost:15672` 看 `inspection.queue` 消息堆积；后端日志 grep `InspectionReportConsumer` | 重启 backend 让 Consumer 重新绑定；`docker compose restart rabbitmq`（DLQ 里的消息需手动 requeue） |
| 6 | nginx 反代下 SSE 卡死 / 一次性吐所有事件 | `proxy_buffering` 未关 | `curl -I` 看 `X-Accel-Buffering` | nginx `location /api/push/` 加 `proxy_buffering off`（见 §4.4） |
| 7 | MCP 巡检报「MCP 客户端握手超时」 | `MCP_ACCESS_TOKEN` 不一致 / MCP Server 端点被拦截 | 后端日志 grep `McpInspectionSession` / `McpAccessFilter` | `.env` 两端 `MCP_ACCESS_TOKEN` 对齐；或留空（仅内网）；巡检 `McpInspectionSession` 是 `AutoCloseable`，异常会自动 close 不泄漏 |
| 8 | AI 返回 JSON 含 ```json fence 但前端解析失败 | `AiJsonFallbackUtil` 已 unwrap，前端二次 parse 失败 | 浏览器 console 看 parse 错误 | 后端已经 fallback 到纯文本，前端 `escapeHtml.js` 渲染纯文本即可；不要前端再 `JSON.parse`，直接渲染 `answer` 字段 |
| 9 | `Could not resolve placeholder 'DEEPSEEK_API_KEY'` 启动失败 | `.env` 缺失 / 工作目录不对 / OS env 覆盖空值 | 启动日志直接报 | 工作目录 `backend/` 或项目根（dev 双候选）；清 IDEA Run Configuration 残留 env；`cp .env.example .env` 重填 |
| 10 | `code=429` 但用户没刷 | 桶里残留旧令牌 / 测试期间触发过 | wait 1s 即恢复（令牌桶每秒补 2 个） | 等待；或重启 backend 清空 `ConcurrentHashMap`（仅单实例有效） |

### 6.2 回滚策略

| 场景 | 回滚动作 | 影响范围 |
|------|----------|----------|
| DeepSeek 整体不可用（Key 泄漏 / 配额耗尽） | `.env` 改 `DEEPSEEK_ENABLED=false` → 重启 | 所有 `/api/ai/**` 返回 503；设备/告警/日志 CRUD 不受影响 |
| 单接口要降级（如巡检卡） | nginx 或网关层把 `/api/ai/agents/inspection-report` 临时 return 423 | 其他 AI 接口（chat / summary / diagnose）正常 |
| SSE 推送链路异常（Consumer 死锁） | `docker compose restart backend`；RabbitMQ 不重启，消息在队列里等 Consumer 恢复 | 已发出的 SSE 连接断开，浏览器自动重连 |
| 误把演示数据灌进生产 | 见 [ADR 0019 §5](../decision-log/0019-flyway-migration.md) | `db/seed/dev/seed_demo_data.sql` 只能 `scripts/seed-dev.sh` 显式执行，不进 Flyway 迁移链 |
| AI 限流误伤 ADMIN | `application-dev.yml` 加 `rate.limit.ai.adminPermits: 100` 重启 | 不影响普通用户桶 |

### 6.3 Day 89 兜底工具速查（事故时的安全网）

| 工具 | 位置 | 防的事故 |
|------|------|----------|
| [escapeHtml.js](../../frontend/src/utils/escapeHtml.js) | 前端 4 个 AI 页面 | AI 自由文本 XSS（OWASP 5 字符实体） |
| [AiJsonFallbackUtil](../../backend/src/main/java/dev/reboot/util/AiJsonFallbackUtil.java) | AiService.summarizeAlarm/diagnose | AI 返回非法 JSON / ```json fence / 超长 payload OOM（2MB 硬上限） |
| [AiRateLimitInterceptor](../../backend/src/main/java/dev/reboot/security/AiRateLimitInterceptor.java) | order=-1 先于通用限流 | DeepSeek 成本级攻击（每用户独立桶，ADMIN 放宽） |
| `McpInspectionSession` AutoCloseable | MCP 巡检 | SSE 握手异常时连接泄漏 |
| Redis SETNX 幂等键 | AiAlarmAutoCreator / InspectionReportConsumer | 跨实例重复报警 / 重复消费 |

---

## 7. 验证矩阵（验收清单）

| Step | 端点 | 角色 | 期望 | ✓ |
|------|------|------|------|---|
| 1.3 | `POST /api/ai/chat` | ADMIN | 200 + `answer`/`totalTokens` 非空 | ☐ |
| 2.1 | `POST /api/rag/documents` | ADMIN | 200 + `chunkCount > 0` | ☐ |
| 2.2 | `SELECT FROM knowledge_chunk` | — | ≥1 行 | ☐ |
| 2.3 | `POST /api/rag/ask` | ADMIN | 200 + `citations` 非空 | ☐ |
| 3.1 | `POST /api/ai/agents/inspection-report` | ADMIN | 200 + `detectedIssues` 数组 | ☐ |
| 3.2 | `SELECT FROM alarm` | — | ≥1 行 `source=AI` 或 `alarm_type LIKE 'AI_%'` | ☐ |
| 3.3 | 二次巡检 `autoAlarmCount` | ADMIN | 与第一次相同（幂等） | ☐ |
| 4.1 | 浏览器 `/inspection` 页 | ADMIN | 连接状态绿色 + 收到日报卡片 | ☐ |
| 4.2 | `GET /api/push/inspection?token=` | ADMIN | 200 + `text/event-stream` + `event: inspection-report` | ☐ |
| 5.4 | 连续 3 次 `/api/ai/chat`（VIEWER） | VIEWER | 200 / 200 / 429 | ☐ |
| 5.5 | 连续 6 次 `/api/ai/chat`（ADMIN） | ADMIN | 6 个 200 | ☐ |

---

## 8. 相关文档索引

| 想了解 | 看 |
|--------|----|
| 巡检推送链路完整 ASCII 图 | [Application-Architecture.md §2a](../Architecture/Application-Architecture.md) |
| DeepSeek 集成决策 | [ADR 0021](../decision-log/0021-deepseek-llm-provider.md) |
| Spring AI ChatClient 抽象 | [ADR 0022](../decision-log/0022-spring-ai-chatclient.md) |
| Function Calling 3 轮硬限 | [ADR 0023](../decision-log/0023-function-calling.md) |
| RAG 向量库选型 | [ADR 0024](../decision-log/0024-rag-vector-store.md) |
| Agent ReAct 循环治理 | [ADR 0026](../decision-log/0026-agent-loop-governance.md) |
| MCP Server 边界 | [ADR 0027](../decision-log/0027-mcp-tool-exposure.md) |
| MCP 客户端鉴权 | [ADR 0029](../decision-log/0029-mcp-client-auth-smoke.md) |
| Agent + MCP 联调 | [ADR 0030](../decision-log/0030-mcp-agent-inspection.md) |
| 推送链路架构边界冻结 | [ADR 0031](../decision-log/0031-day85-ai-report-push-architecture.md) |
| 密钥 SSOT | [ADR 0015](../decision-log/0015-dev-env-secrets-ssot.md) + [AGENTS §8](../../AGENTS.md) |
| Day 89 重构细节 | [Day89.md](../../backend/DAILY/Day89.md) |

---

> 完成时间：2026-09-03（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
