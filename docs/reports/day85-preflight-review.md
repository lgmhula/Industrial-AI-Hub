# Day85 Preflight Review

> Week12 Exit Review 完成 → ADR-0031 冻结后的开工前置审计。
> 只读检查，不含 Day85 任何业务代码实现。

| 字段 | 值 |
|------|-----|
| 生成时间 | 2026-08-31 21:25 (Asia/Shanghai) |
| 执行分支 | `docs/week12-review` |
| HEAD | `f0311bb` Day 082 |
| 关联 | Week12 Exit Review / week12-exit-gate-report / ADR 0031 |

## 1. Git 状态

```
CURRENT_BRANCH: docs/week12-review
HEAD: f0311bb Day 082: MCP 客户端集成与传输鉴权冒烟（ADR 0029）
WORKTREE_NOT_CLEAN
```

未提交内容分两类：
- **Gate 任务产物（本审查前置）**：`OperationLogAspect.java`(M)、`OperationLogAspectTest.java`(M)、`0031-day85-ai-report-push-architecture.md`(??)、`week12-exit-gate-report.md`(??) —— P1 修复 + ADR + 报告，270 测试全绿，已验证
- **Day83/84 未提交**：McpInspectionAgentService/McpToolCallbackAdapter/McpInspectionSession/AiInspectionReportResult/V12/V13/6 测试/ADR 0030/Day83.md/Day84.md/Week12.md 等

杂散 AI 产物：`docs/reports/Industrial-AI-Hub-对话总结与项目理解-20260831.md`(??)，疑似中间产物。

未自动清理，按指令仅记录。

## 2. 当前架构确认

### 2.1 RabbitMQ 基础设施（已就绪，可复用模式）

[MQConfig.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/config/MQConfig.java) `@Profile("!test")`，全景声明：

| 模式 | Exchange | Queue | Day |
|------|----------|-------|-----|
| 工作队列 | `alarm.exchange` (Direct, durable) | `alarm.queue` (durable, maxPriority=10, DLX→alarm.dlx, TTL 30s) | 51 |
| 死信 | `alarm.dlx` (Direct, durable) | `alarm.dlq` (durable) | 53 |
| 延迟 | `alarm.delay.exchange` + `alarm.delay.dlx` | `alarm.delay.queue` (DLX→delay.dlx) + `alarm.escalation.queue` | 54 |
| 发布订阅 | `device-data.fanout` (Fanout, durable) | `device-data.log.queue` + `device-data.analytics.queue` | 52 |

- **Producer**：[AlarmProducer](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/AlarmProducer.java)、[DeviceDataProducer](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/DeviceDataProducer.java)（RabbitTemplate + JSON 转换）
- **Consumer**：AlarmConsumer（`@RabbitListener` concurrency=2-4）、AlarmEscalationConsumer、DeviceDataSyncConsumer
- **JSON 转换器**：`Jackson2JsonMessageConverter` Bean + RabbitTemplate 接入
- **消息 DTO 范例**：AlarmMessage、DeviceDataMessage（Day85 可仿写 InspectionReportMessage）
- **ack 模式**：`application.yml` `acknowledge-mode: auto`（无显式 retry 块，依赖 DLX 兜底失败消息）
- **inspection.\* 队列**：❌ 不存在（Day85 必须新增）

### 2.2 AI Agent 输出链路（已就绪）

[McInspectionAgentService.generate()](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/service/McpInspectionAgentService.java#L67) → `AiInspectionReportResult`（[DTO](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/dto/ai/AiInspectionReportResult.java) 含 reportDate/report/rounds/calls/devices/alarms/truncated/toolTrace + 审计 toString）。
[AiController.inspectionReport()](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/controller/AiController.java#L116) 返回 `ApiResponse<AiInspectionReportResult>`，`@RequireRole(ADMIN)` + `@OperationLog(INSPECTION/MCP,{ret})`。
→ Day85 只需在 generate() 末尾增加「投递 InspectionReportMessage 到 MQ」一步，不改动 Agent 主链路。

### 2.3 站点权限链（已就绪）

- [SiteAccessService](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/service/SiteAccessService.java)（class L30，全局 ADMIN 放行 + 站点内角色断言）
- [UserSiteMapper](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mapper/UserSiteMapper.java)（`SELECT site_id FROM user_site WHERE user_id=?`）
- DeviceMapper/AlarmMapper 已有 `site_id IN (...)` 作用域过滤
- `user_site` 表由 V4 迁移引入（ADR 0020）

### 2.4 JWT SSE 鉴权链（已就绪）

- [JwtAuthFilter](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/security/JwtAuthFilter.java)（class L34，读 Authorization header L54）
- [AuthInterceptor](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/security/AuthInterceptor.java)（class L32，HandlerInterceptor）
- 未来 `/api/push/inspection` 走同链路：JWT Filter → AuthInterceptor → PushController，无需另建鉴权。

### 2.5 Redis 能力（已就绪，多副本可扩展）

- [RedisConfig](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/config/RedisConfig.java) 提供 StringRedisTemplate + objectRedisTemplate
- 已用于 AuthRateLimitService、TokenBlacklistService、CacheService、@Cacheable
- Redisson 分布式锁可用
- **Pub/Sub**：RedisTemplate 原生支持 `convertAndSend`/`MessageListener`，Day85 多副本路由可直接用；单副本进程内直连无需启用

## 3. ADR-0031 一致性检查

| ADR-0031 声明 | 代码现状 | 一致性 |
|--------------|---------|--------|
| 前半段 Agent→MQ→Consumer 有基础设施 | MQConfig + Producer/Consumer 范例齐全 | ✅ 一致 |
| 后半段 Consumer→Push Gateway→SSE→Vue 完全空白 | grep SseEmitter/WebSocket/MessageMapping/PushGateway 零命中；frontend EventSource/WebSocket 零命中 | ✅ 一致 |
| 禁止 RabbitMQ→Browser 直连 | 无浏览器直连 RabbitMQ 代码 | ✅ 一致 |
| 推荐 SSE（SseEmitter） | 项目无 SSE 存量，Spring MVC 原生支持，无新依赖 | ✅ 可行 |
| 站点隔离复用 SiteAccessService | SiteAccessService 存在 | ✅ 一致 |
| 复用 alarm.dlx 模式做 inspection DLQ | MQConfig 模式可直接复制 | ✅ 一致 |
| InspectionReportMessage DTO 待建 | 全仓 grep 仅在 ADR-0031 文档命中，无代码 | ✅ 一致（待建） |

ADR-0031 与代码现状零冲突，可执行性 PASS。

## 4. 已具备能力

| 能力 | 证据 |
|------|------|
| RabbitMQ 连接/配置 | application.yml L57-65 + Docker 容器运行 |
| Exchange/Queue 规范 | MQConfig 4 套模式（工作队列/死信/延迟/发布订阅） |
| Producer 范例 | AlarmProducer、DeviceDataProducer |
| Consumer 范例 | AlarmConsumer（concurrency + DLQ）、AlarmEscalationConsumer、DeviceDataSyncConsumer |
| DLQ 策略 | alarm.dlx→alarm.dlq 绑定 |
| 延迟队列 | TTL+DLX→escalation 模式 |
| JSON 消息转换 | Jackson2JsonMessageConverter |
| 消息 DTO 范例 | AlarmMessage、DeviceDataMessage |
| AI Agent 日报生成 | McpInspectionAgentService.generate()→AiInspectionReportResult |
| 站点作用域 | SiteAccessService + user_site + site_id 过滤 |
| JWT 鉴权链 | JwtAuthFilter + AuthInterceptor |
| Redis 客户端 | StringRedisTemplate + Redisson |
| 缓存能力 | @Cacheable + CacheConfig |

## 5. 缺失能力

| 缺失项 | Day85 需建 | 优先级 |
|--------|-----------|--------|
| `InspectionReportMessage` DTO | 新建（仿 AlarmMessage） | Step 1 |
| `inspection.exchange` / `inspection.queue` / `inspection.dlx` / `inspection.dlq` | MQConfig 增声明 + 绑定 | Step 2 |
| `InspectionReportProducer` | 新建（Agent 侧投递） | Step 3 |
| `InspectionReportConsumer` | 新建（@RabbitListener + 路由） | Step 4 |
| `InspectionPushGateway` + `SseEmitterRegistry` | 新建（userId→emitter 注册表） | Step 5 |
| `InspectionPushController` | 新建（`/api/push/inspection`，JWT） | Step 6 |
| Vue EventSource 订阅 | 新建前端组件 + composable | Step 7 |
| Nginx SSE 指令 | `proxy_buffering off; proxy_cache off;` 加到 `/api/push/` location | Step 7 前置 |
| `InspectionPushGatewayTest` | 新建 | Step 8 |
| `InspectionReportConsumerTest` | 新建 | Step 8 |
| `SiteIsolationTest`（SSE 跨站点） | 新建 | Step 8 |
| `SseReconnectTest` | 新建 | Step 8 |
| 幂等去重键 `inspection:{reportDate}:{siteId}` | Redis SETNX 24h | Step 9 |
| Application-Architecture 增推送链路图 | 文档同步 | Step 10 |

## 6. Day85 实施风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| **Nginx proxy_buffering 默认 on** | SSE 事件被反代缓冲，浏览器收不到实时推送 | Day85 必须在 nginx.conf 增 `location /api/push/ { proxy_buffering off; proxy_cache off; proxy_read_timeout 3600s; }` |
| **RabbitMQ acknowledge-mode=auto 无 retry 块** | Consumer 抛异常时 auto-nack，依赖 DLX 兜底，无指数退避 | Day85 inspection.queue 沿用 DLX 模式即可；如需 retry 在 application.yml 增 `listener.simple.retry.enabled=true` |
| **工作区未提交** | Day85 若在 dirty worktree 开新分支，Gate 修复 + Day83/84 易混入 | 开工前先提交 Gate 产物到 `feat/ai-exit-gate` 或合并 Day83/84 到 `feat/agent-mcp`，再开 `feat/day85-ai-push` |
| **分支命名混乱** | `docs/week12-review` 含 Day83 代码，Day85 再叠代码更乱 | 新建 `feat/day85-ai-push` 分支，从干净基线起步 |
| **SSE emitter 内存泄漏** | 长连接未清理导致 OOM | SseEmitter 设 timeout（如 30min）+ 定时清理 + shutdown hook |
| **单副本路由假象** | 进程内直连掩盖多副本路由缺口 | 接口契约按「可换 Redis pub/sub」设计（ADR-0031 §3.2） |
| **DeepSeek 真实 key 在 .env** | Day85 联调会消耗真实 API 配额 | 已确认 key 有效；Agent 6 轮硬限兜底成本 |
| **前端无测试基建** | frontend grep `.test.*` / `__tests__/` 零命中 | Day85 前端测试可后置，优先后端链路 |

## 7. 开工前必须完成事项

| 序 | 事项 | 是否阻塞 Day85 |
|----|------|---------------|
| 1 | 提交 Gate 任务产物（OperationLogAspect P1 修复 + ADR-0031 + 测试 + exit-gate-report） | 否（但强烈建议，避免 dirty worktree 污染 Day85） |
| 2 | 决定 Day83/84 归属（`feat/agent-mcp` 合并或新分支） | 否（治理问题，可与 Day85 并行） |
| 3 | 新建 `feat/day85-ai-push` 分支从干净基线起步 | 否（推荐做法） |
| 4 | 删除或归档杂散 AI 产物 `Industrial-AI-Hub-对话总结...20260831.md` | 否（卫生问题） |
| 5 | nginx.conf 增加 SSE location 计划（不提前改，在 Step 7 落地） | 否（设计阶段已识别） |

**无硬阻塞项**。所有依赖（MQ/Site/JWT/Redis/Agent）已就绪，缺失项均为 Day85 自身构建工作。

## 8. Day85 实施建议顺序

```
Step 1  InspectionReportMessage DTO（reportDate/siteIds/report/rounds/calls/devices/alarms/truncated，Serializable）
Step 2  MQConfig 增 inspection.exchange(Direct,durable) + inspection.queue(durable,DLX→inspection.dlx,TTL) + inspection.dlq + 绑定
Step 3  InspectionReportProducer（Agent 侧，RabbitTemplate.convertAndSend，Agent.generate() 末尾投递，不阻塞主流程）
Step 4  InspectionReportConsumer（@RabbitListener，解析 siteIds，调 PushGateway，手动 ack）
Step 5  InspectionPushGateway + SseEmitterRegistry（userId→emitter 表，emitter 建连时绑定 siteIds，timeout 30min，定时清理）
Step 6  InspectionPushController（GET /api/push/inspection，JWT+AuthInterceptor，返回 SseEmitter）
        └─ Step 6 前置：nginx.conf 增 location /api/push/ { proxy_buffering off; proxy_cache off; proxy_read_timeout 3600s; }
Step 7  Vue EventSource 订阅（composable + 组件 + 断线原生重连 + 渲染日报）
Step 8  权限隔离测试（InspectionPushGatewayTest / InspectionReportConsumerTest / SiteIsolationTest / SseReconnectTest）
Step 9  幂等测试（inspection:{reportDate}:{siteId} Redis SETNX 24h 去重）
Step 10 文档同步（Application-Architecture 增推送链路图 + AGENTS §3 + DAILY/Day85.md + Week13 复盘准备）
```

每步可独立 PR/commit，Step 5 是最大不确定点（emitter 生命周期），建议先单副本进程内直连，多副本前切 Redis pub/sub。

---

## DAY85 PREFLIGHT COMPLETE

READY: **YES**

BLOCKERS: 无

NEXT ACTION: 提交 Gate 产物 → 新建 `feat/day85-ai-push` 分支 → 从 Step 1（InspectionReportMessage DTO）开工

---

> 维护者：AI 助手 + hula0710 | 路径遵循 AGENTS §2（`docs/reports/`）
> 本报告为只读审计产物，未创建任何业务代码、未修改 ADR-0031、未实现 SSE/Consumer/Gateway。
