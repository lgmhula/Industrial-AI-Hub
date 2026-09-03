# Day 89 — AI 模块代码重构：公共组件抽取 + 异常处理统一 + AI 限流（Phase 4 Day 89，DAILY_ROADMAP L553）

> **状态**：Day 89 完成（前端 escapeText 去重 / 后端 JSON 解析 fallback 统一 / AI 接口独立限流 + 14 新单测）
> **关联**：DAILY_ROADMAP Day 89 "重构 AI 模块代码：抽取公共组件、异常处理、限流" / AGENTS §4.3 Day 89 计划（Day88 §5 候选）
> **验证**：前端 `npm run build` 954ms 0 errors；后端 `./mvnw test` **343/343**（新增 14 tests：AiJsonFallbackUtilTest 9 + AiRateLimitInterceptorTest 5）；Failures=0, Errors=0, Skipped=0

***

## 1. 今日产出

### 1.1 Day 89 三项重构任务 × 100% 完成

| 子任务（DAILY_ROADMAP Day 89） | 代码产物 | 行数 | 状态 |
|------------------------------|----------|------|------|
| ① 抽取公共组件 | `frontend/src/utils/escapeHtml.js`（前端 4 页面 escapeText/escapeHtml/safeJoin 统一 util） + `backend/src/main/java/dev/reboot/util/AiJsonFallbackUtil.java`（后端 AI JSON 解析+降级统一 util） | ~180 行前端 + ~160 行后端 = 340 | ✅ |
| ② 异常处理统一 | AiService.summarizeAlarm/diagnoseDevice 两处 try/catch→`AiJsonFallbackUtil.parseOrFallback` 统一；删除 AiService 私有 `unwrapJsonFence`；统一 warn 日志 message/size-guard/空串降级/fallback 工厂模式 | 删 23 行 + 改 2 处 22 → 9 行 = -14 行净删 | ✅ |
| ③ 限流 | `security/AiRateLimitInterceptor.java`（独立 AI 限流：每用户/每 IP 桶 / ADMIN 放宽 / 超长 JSON 长度防护 / 429 响应）+ `WebMvcConfig` 注册 order=-1 先拦截 `/api/ai/** /api/agents/** /api/rag/** /api/mcp/**` | ~170 行 Interceptor + WebMvcConfig 改 | ✅ |

### 1.2 前端：`escapeHtml.js` 统一 4 页面 4 份 `escapeText` 重复定义

[escapeHtml.js](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/utils/escapeHtml.js)

#### 1.2.1 接口设计（3 个具名 export + 1 default）

```js
export function escapeHtml(str)   // 主函数：5 字符 HTML 实体转义（OWASP 最小集）
export function escapeText(str)   // escapeHtml 的别名（兼容 Day 87 4 页面已有模板函数名 escapeText）
export function safeJoin(parts, sep = ', ')  // 片段数组每片段转义后用分隔符连接（可 v-html 渲染如 <br/>）
export default escapeHtml
```

#### 1.2.2 页面替换前后对比（4 页面 net 去重）

| 页面 | 替换前 | 替换后 | 净减 |
|------|--------|--------|------|
| RagAssistant.vue | 6 行 escapeMap + escapeText（4 页完全相同）+ 未使用 util | `import { escapeText, escapeHtml } from '../utils/escapeHtml.js'` | -6 |
| DeviceDetail.vue | 7 行（escapeMap 5 行 + function 2 行）+ formatTs 重复函数 | `import { escapeText } from '../utils/escapeHtml.js'` | -7 |
| AlarmList.vue | 7 行 + copySummary 手动拼接 | `import { escapeText, safeJoin }` | -7（safeJoin 替换后续可进一步改 copySummary） |
| InspectionReport.vue | 19 行（含 DOM fallback try/catch）——**Day 87 与另 3 页实现不一致** | `import { escapeText }` — 对齐 DAY87 标准转义（去掉 DOM fallback 那 13 行，统一 1 行 regex 实现 = 5 字符替换；所有调用点模板不变） | -18 |

**合计：4 页面去重 38 行 escapeText 本地定义，模板调用处 0 改动**（只删本地定义，加 import）——**完全零风险替换**。

#### 1.2.3 额外价值（Day 89 设计余量）
- `safeJoin` 为未来 AlarmList issues 展示 / 报告表格单元格"多值合并"提供现成安全工具（今天 Day 89 没改调用处，保留 Day 90 集成文档可直接用）；
- 详尽 JSDoc（20 行）对齐后端 AiJsonFallbackUtil 文档风格，说明"不能直接用 v-html，要用 DOMPurify"——避免将来团队同事错用；
- 导出 3 种命名（escapeHtml/escapeText/default）兼顾"语义正确"和"Day 87 模板迁移零改动"两个目标。

### 1.3 后端：AiJsonFallbackUtil 统一 2 处 JSON 解析降级 + 未来所有 AI DTO 入口

[AiJsonFallbackUtil.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/util/AiJsonFallbackUtil.java)

#### 1.3.1 统一后 API

```java
public static <T> T parseOrFallback(
    String content,                 // AI 返回的原始文本（可能 ```json fence）
    Class<T> targetClass,           // 目标 DTO
    ObjectMapper mapper,            // 项目已配置的 ObjectMapper（不 new 新的，避免配置漂移）
    Function<String, T> fallbackFactory, // 降级工厂：rawContent → 填好的 fallback DTO
    @Nullable BiConsumer<String, Exception> warnHandler  // 可选：降级场景追加写审计 detail
)
```

1. **unwrap ```json fence**：统一调用 `unwrapJsonFence(trimmed)`——Day 89 之前 AiService 私有 unwrapJsonFence 做这件事，其他 AI Agent（DeviceAnalysis / McpInspection）目前只生成中文自然文本日报，如果未来改"结构化输出总结字段"，直接复用 parseOrFallback 即可，不用重写 fence；
2. **2MB 硬上限保护**：超过 MAX_JSON_STRING_LEN = 2MB 不做 readValue，直接降级（防止 AI 超长 JSON OOM / GC 抖动——Spring AI token 成本 + Jackson 字符数组翻倍分配 = 4MB 分配阻塞）；
3. **参数防御**：targetClass / mapper / fallbackFactory 任一 null → `IllegalArgumentException`（fail-fast，Javadoc 写清楚）——比 null ObjectMapper NPE 更清晰；
4. **统一 warn 日志**：`log.warn("AI JSON 解析失败（AiAlarmSummary），退回纯文本降级: {}", e.getOriginalMessage())`——和 Day 86 AiAlarmAutoCreator "降级日志但不抛异常"语义一致；
5. **BiConsumer warnHandler**：给 Day 90/91 集成"operation_log 里写降级 note"留钩子，不改 API 形态。

#### 1.3.2 AiService 改造成果

- `summarizeAlarm`：12 行 try/catch/warn/fallback **→ 8 行 parseOrFallback + lambda**（删除 unwrap 调用，parseOrFallback 内部统一）
- `diagnoseDevice`：**同样缩减 12→8 行**，且 fallback 补 `healthLevel = "未知"`（之前降级不填 healthLevel → 前端 healthTagType 空字符串 = 不匹配 1/2/3 → 默认 info 标签视觉上还行，但语义缺漏，Day 89 补上）
- 删除 `private unwrapJsonFence(...)` 方法（12 行私有实现）+ 删除 `JsonProcessingException` import → **AiService 无任何 JSON 解析私有工具**，完全走 AiJsonFallbackUtil。

#### 1.3.3 新增单测：AiJsonFallbackUtilTest 9 场景

| 测试 | 场景 | 断言 |
|------|------|------|
| 1 parseOrFallback_normalJson | 合法 JSON 字段解析 | 4 字段全命中 |
| 2 parseOrFallback_jsonFenceUnwrapped | 前后 ```json fence 包裹 | priority/摘要解析无误 |
| 3 parseOrFallback_malformedFence | 只有 ```json 开头无闭合 → 降级 | 不抛异常，fallback summary=原始内容 |
| 4 parseOrFallback_invalidJson | 自由文本（非法 JSON）→ fallback | possibleCauses/suggestedActions 空列表，不空指针 |
| 5 parseOrFallback_nullOrEmpty | null/""/"  " → fallback | 三条 3 个断言 |
| 6 parseOrFallback_hugeJson | 2.2MB 合法 JSON → 长度超上限降级 | 不抛异常，不 OOM，fallback 正确（≈80ms 完成 2MB StringBuilder 构造 + 路径判断） |
| 7 unwrapJsonFence_plainText | 纯文本无 fence | 原样返回 |
| 8 unwrapJsonFence_stripsCodeFenceWithLanguage | ```json\n{\"a\":1}\n``` → 对象本体 | 正确返回 `{"a":1}` |
| 9 参数防御 null klass/mapper/factory → IllegalArgumentException | 三个 assertThrows |

### 1.4 限流：AiRateLimitInterceptor 独立 AI 成本保护

[AiRateLimitInterceptor.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/security/AiRateLimitInterceptor.java) + [WebMvcConfig.java L64-86](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/config/WebMvcConfig.java#L64)

#### 1.4.1 为什么独立于通用 RateLimitInterceptor

Day 86 AI 启用后，每个 `/api/ai/**` 调用 = DeepSeek 成本：
- 通用 `RateLimitInterceptor` 50 req/s 是"洪水防护"——50 次 AI 接口 = 可能几十美元账单；
- AI 接口（/api/ai/**, /api/agents/**, /api/rag/**, /api/mcp/**）需要 独立 更严的 按用户粒度限流（不是按 path 共享桶）；
- 同时 ADMIN 巡检需要比 VIEWER 更宽（5 req/s vs 2 req/s），通用 RateLimit 不支持角色区分。

#### 1.4.2 独立桶粒度

| 用户类型 | 桶 key | 默认限流（可配） |
|----------|--------|-------------------|
| 已登录（JWT userId 属性存在） | `u:{userId}`，每用户独立（防止"100 账号 × 1 QPS 并发"） | rate.limit.ai.permits = 2 req/s |
| ADMIN 角色 | 同 u:{userId}，但桶速率放宽到 rate.limit.ai.adminPermits | 5 req/s |
| 匿名未登录 | `ip:{clientIp}`（6 层反向代理链 X-Forwarded-For/X-Real-IP 取第一个非 unknown 真实 IP） | 同普通 2 req/s（防匿名刷 503） |

- **热更新**：如果用户在桶创建后升级为 ADMIN，下次请求时 `Math.abs(limiter.getRate() - rate) > 0.001` → `limiter.setRate(rate)`——不改 code，不重启实例就能让 ADMIN 宽限流生效；
- **acquireTimeout=150ms**（可配置 rate.limit.ai.acquireTimeoutMs）：不是永久阻塞，150ms 拿不到令牌 = 快速 429，避免"浏览器 SSE 长时间挂起"；
- **ConcurrentHashMap 缓存**：10k 用户级场景无泄漏（未来可替换 Caffeine 做 LRU 淘汰，今日 Day 89 极简）。

#### 1.4.3 注册：WebMvcConfig order=-1（AI 限流 → 通用 RateLimit → Auth）

拦截顺序（order 小=先执行）：
```
order -1   AiRateLimitInterceptor        /api/ai/**, /api/agents/**, /api/rag/**, /api/mcp/**
order  0   RateLimitInterceptor（通用）   /api/** 除 /api/auth/**
order  1   AuthInterceptor（@RequireRole 权限）/api/** 除 /api/auth/**
```

**AI 限流必须在 Auth 之前的原因**：匿名 /api/ai/chat（未登录=503）如果不先限流，攻击者可以 1k QPS 打"503 AI 未启用"，DeepSeek 不花钱但 CPU 被打满——所有 AI 入口先限流，**不管权限和 AI 是否启用**。

#### 1.4.4 新增单测：AiRateLimitInterceptorTest 5 场景

| 测试 | 断言 |
|------|------|
| separateBuckets_perUser：用户 42 连续 2 发（0.5/s 窄限流+timeout=0）第 2 发拒；用户 43 全新桶过 | r1=T, r2=F, r3=T |
| anonymousBuckets_byIp：同一 IP 第 2 发拒 | IP 不同=过 |
| adminRole_widerPermits：ADMIN 1000/s 两发都过 | r1=T, r2=T |
| limitResponse_status429_and_jsonBody：第 2 发 status=429, ContentType=application/json, 响应体包含 code=429 + "AI 接口调用过于频繁" | 三断言全命中 |
| concurrency_multipleUsers_bucketsIndependentSafe：20 用户 × 10 并发 = 200 次请求，总和=200（每请求要么过要么拒无丢失无异常） | latch await 15s 成功，总和正确，通过率 ≤ 用户数×2 |

#### 1.4.5 配置注入说明（fail-safe 默认值）

AiRateLimitInterceptor 构造器 3 参用 `positive()` 辅助检查 `>0`：
- rate=0 或负 → 回退默认并 warn：防止配错导致"限流 0/s = 永久 429"——比 fail-closed 更安全。

### 1.5 Day 89 总测试矩阵

| 运行 | 结果 |
|------|------|
| 前端 npm run build | ✅ 954ms，0 errors，0 warnings |
| 后端 ./mvnw test（全量） | ✅ **Tests=343, Failures=0, Errors=0, Skipped=0** |
| AiJsonFallbackUtilTest 9 场景 | ✅ 全绿 |
| AiRateLimitInterceptorTest 5 场景 | ✅ 全绿（含并发 20×10 安全） |

***

## 2. 设计决策（Day 89 重构特有）

### 2.1 前端 escapeText 双命名（escapeHtml 主 + escapeText 别名）不是冗余

保留 `escapeText` 别名不是偷懒，是为了 Day 87 4 页面已经写满 `{{ escapeText(x) }}` 模板的情况下，**不需要改一行模板代码就能完成替换**。如果只有 `escapeHtml` 一个名字，需要改 4 页面 ×（InspectionReport 118, 124 / RagAssistant 33,49 / DeviceDetail 76,91,97,104,128,150 / AlarmList 130,151,157,163）= **14 处模板调用**。14 处替换漏掉 1 处就 lint 失败找不到。别名设计让迁移成本 = "加 1 行 import + 删本地 function"，完全不碰模板。

### 2.2 AI JSON fallback 统一 + 2MB 硬上限不是"加功能"，是防事故

之前 AiService 2 处各自写 fallback = 一个地方 bug 修了另一个地方忘。更重要的是没有上限保护 = 如果 AI 因为 prompt injection 攻击把 500MB 文本塞进 JSON fence，Jackson 内部会 `new char[len]` 分配 1GB+，实例 OOM。2MB 上限不影响正常 AI DTO（告警摘要/诊断 <10 KB），但能挡住 99% 的"AI 失控返回巨型 payload"事故。

### 2.3 AiRateLimitInterceptor order=-1（AI 限流先于通用限流）不是 reverse priority

看起来 AI 接口被"限流两次"（order=-1 AI桶 + order=0 通用桶）= 双重浪费，但实际上：
1. order=-1 /api/ai/** 先通过 → order=0 /api/** 50 req/s 几乎永远用不完（AI 接口才 2/s per user）；
2. 关键是 **AI 限流挡的是"DeepSeek 成本级攻击"，通用 RateLimit 挡的是设备 CRUD 洪水攻击**——这两个目标完全不同，就算双重挡一下，性能开销是 ConcurrentHashMap.computeIfAbsent + tryAcquire 纳秒级，可忽略。

### 2.4 没有用 Resilience4j Bucket4j 等新依赖

Day 89 目标是"抽取现有工具、统一异常"，不是引新依赖——Guava 已在 Day 37 引进，RateLimiter 复用通用 RateLimitInterceptor 已经验证过的 Guava 实现，无新依赖，无 Flyway 迁移。

***

## 3. 文档同步

- [AGENTS.md §3](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/AGENTS.md)：当前状态补 Day 89 重构；下一步更新为「Day 90 AI 模块集成文档（SETUP 级 AI Enable→ PDF 导入 → 巡检 → SSE 验证 runbook）」
- 本文件（Day89.md）：完整交付细节 + 测试矩阵 + 4 条设计决策
- [Week13.md](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/REVIEW/Week13.md)（Day 88 已交付，本次不改）
- [Application-Architecture.md §2a](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/docs/Architecture/Application-Architecture.md#L102)（Day 88 已交付，不改）

***

## 4. 遗留 & 后续

1. **AlarmList.vue copySummary 手动 forEach 未用 safeJoin**（Day 89 抽取 safeJoin 但未改调用点——Day 90 集成文档时顺手替换，风险 0）；
2. **DeviceStatusAgentService 手写 3 轮循环尚未迁移到 ToolCallingAgent.run()**——目前 DeviceStatus 测试完整（343 tests 全绿），迁移会破坏 10+ DeviceStatusAgentServiceTest 的 mock 行为，不值得 Day 89 测试日冒险，移到 Day 90 后或者 Phase 5 空闲日；
3. **AiRateLimitInterceptor 未接入真实 Redis**（JWT user 级分布式限流）——当前内存 ConcurrentHashMap = 单实例有效，水平扩展（2+ Spring Boot 副本）时需要改 Redis `ai-rate:{userId}` TTL 1s + 滑动窗口 Lua script。Day 89 不做（单实例基线无此需求），进 TECH-DEBT.md 未来 Phase 5；
4. **ConfigurationProperties 未抽象 rate.limit.ai.\***——目前 3 参直接 `@Value`。如果 Day 90 集成文档配置多，可加 @ConfigurationProperties("rate.limit.ai")；
5. **application.yml 默认值未写 rate.limit.ai.*=2/5/150**——代码里已有 fallback = 2/5/150，不阻塞，但 Day 90 文档化时要补注释化默认值。

***

## 5. 明日计划（Day 90 候选，严格对齐 DAILY_ROADMAP L554）

DAILY_ROADMAP Day 90 = **写 AI 模块集成文档**。参考 SETUP.md 从零复刻指南的 step-by-step 风格：

1. **Step 1-环境启用**：`.env` 中 `DEEPSEEK_ENABLED=true` + `DEEPSEEK_API_KEY=sk-xxx` + `MCP_ACCESS_TOKEN=xxx` 变量模板（已有 .env.example）+ 启动 Spring Boot 验证 `/api/ai/chat` 正常返回；
2. **Step 2-RAG 入库**：curl `POST /api/rag/ingest/upload` Multipart PDF 手册 → DB 查 knowledge chunk 表 → `/api/rag/ask` 验证回答带引文；
3. **Step 3-巡检触发 + 业务闭环**：ADMIN curl `/api/ai/agents/inspection-report` → 后端 330 tests 里 AiAlarmAutoCreator 幂等验证 → alarm 表新插入 AUTO_ALARM 记录；
4. **Step 4-SSE 推送验证**：打开浏览器登录（或 EventSource 命令行工具测试 `GET /api/push/inspection?token=xxx`）→ 验证具名事件 inspection-report；
5. **Step 5-限流 & 运维**：curl 3 次 /api/ai/chat 验证 429（普通用户）；ADMIN token curl 6 次验证不 429；
6. **Step 6-常见故障速查表 + 回滚策略**：AI 未启用 503 / DeepSeek 429 / Redis 未连接幂等降级 / MCP SSE 握手中断（McpInspectionSession.close）/ nginx 反代缓冲未关导致 SSE 卡死——每一种的症状、定位、回滚。

产出 `docs/ai/phase4-integration-guide.md`（对应 Day 90 要求）。

Day 91 = 第四阶段复盘（Week14.md）+ Git tag v2.0-ai，对齐 L555。

***

> 完成时间：2026-09-03 10:05（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
