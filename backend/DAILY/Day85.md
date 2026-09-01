# Day 85 — AI 巡检日报 SSE 推送链路（Phase 1-7：投递 + Agent + Consumer + Push Gateway + Controller + 前端订阅）

> **状态**：Phase 1-7 完成（投递 + 消费 + SSE 推送网关 + 路由 + 浏览器建连 Controller + 前端 EventSource 订阅 + nginx 反代缓冲关闭）/ Day 85 全链路收官
> **关联**：ADR 0031（架构边界冻结）/ Week12 Exit Review / Day 83 巡检 Agent
> **测试**：后端 316/316 全绿（270 基线 + Phase 1 新增 4 + Phase 2 新增 3 + Phase 3 新增 6 + Phase 4 新增 25 + Phase 6 新增 3 + Phase 7 新增 5）；前端 build 744ms 0 错误

***

## 1. 今日产出

### 1.1 Phase 1 — Agent → RabbitMQ 投递侧（ADR 0031 第 1-2 段）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Step 1 | [InspectionReportMessage.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/InspectionReportMessage.java) | 消息 DTO，`implements Serializable`，字段：`reportDate` / `report` / `toolRounds` / `toolCalls` / `deviceCount` / `alarmCount` / `truncated` / `siteIds` / `triggeredByUserId` / `generatedAt`；`setSiteIds(null)` 归一化为空 List（ADMIN 全站点语义） |
| Step 2 | [MQConfig.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/config/MQConfig.java) | 增 5 个常量 + 5 个 Bean：`inspectionExchange`（Direct, durable）/ `inspectionQueue`（durable, DLX→inspection.dlx, **无 TTL**）/ `inspectionDlxExchange` / `inspectionDlq` / `inspectionDlqBinding` / `inspectionBinding`；类 Javadoc 增 Day 85 架构图与设计要点 |
| Step 3 | [InspectionReportProducer.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/InspectionReportProducer.java) | `@Component @Profile("!test")`，构造器注入 `RabbitTemplate`，`send()` 调用 `convertAndSend(INSPECTION_EXCHANGE, INSPECTION_ROUTING_KEY, message)`；类 Javadoc 显式声明「不感知 SSE / Push Gateway / 浏览器连接」边界 |
| Step 4 | [InspectionReportProducerTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/mq/InspectionReportProducerTest.java) | 4 个测试方法：① 正常投递验证 exchange/routingKey/message 透传；② 全字段保留（reportDate 用于幂等键、siteIds 用于路由）；③ 空 siteIds List 透传（ADMIN 全站点语义）；④ null siteIds 归一化为空 List |

### 1.2 Phase 1 验收条件（用户原文）逐条对照

| 验收条件 | 实现 | 验证 |
|----------|------|------|
| Agent 不感知 SSE | `McpInspectionAgentService` 未修改，未引入 SseEmitter/PushGateway 任何依赖 | grep `SseEmitter\|PushGateway` in `McpInspectionAgentService.java` = 0 命中 |
| Agent 不持有用户连接 | 同上；Agent 当前不接入 Producer（Phase 1 范围内），Producer 也只注入 RabbitTemplate | Producer 构造器仅 `RabbitTemplate` 一个依赖 |
| RabbitMQ 不暴露浏览器 | Phase 1 仅投递侧，无 `/api/push/**` 端点；Consumer/SSE Controller 留待 Phase 2-6 | grep `SseEmitter\|@MessageMapping` in `backend/src/main/java` = 0 命中 |
| Message 可 JSON 序列化 | `InspectionReportMessage implements Serializable`，字段全为 Jackson 友好类型（LocalDate/LocalDateTime/String/原始类型/List） | Jackson2JsonMessageConverter 已在 MQConfig#rabbitTemplate 注入 |
| exchange/queue/DLQ 与 alarm 模式一致 | Direct Exchange（durable）+ durable Queue（DLX→DLQ）+ Binding，命名 `inspection.exchange/queue/dlx/dlq` 与 `alarm.*` 同范式；唯一差异：省略 `ttl()`（日报延迟敏感度低，不应 30s 进 DLQ）和 `maxPriority()`（日报无优先级） | MQConfig Javadoc 已显式记录差异理由 |
| Producer 单元测试通过 | `InspectionReportProducerTest` 4/4 pass | surefire 报告：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` |

### 1.3 全量回归

- 主代码编译：`./mvnw -DskipTests compile` exit 0
- 全量测试：`./mvnw test` → **Tests run: 274, Failures: 0, Errors: 0, Skipped: 0**（270 基线 + Phase 1 新增 4），BUILD SUCCESS

***

### 1.3 Phase 2 — Agent 接入 Producer（ADR 0031 §3.1 / §6）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Step 1 | [McpInspectionAgentService.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/service/McpInspectionAgentService.java) | 构造器增 `@Nullable InspectionReportProducer` 参数（参考 DeviceDataService 注入 AlarmProducer 模式，兼容 test profile 下 Producer 不存在）；`generate()` 末尾调用 `dispatchReport(result)`；新增 `dispatchReport()` 私有方法：null 检查 + try/catch AmqpException 降级日志不阻塞；新增 `toMessage()` 转换方法：字段一一映射，`siteIds=List.of()`（ADMIN 全站点语义），`triggeredByUserId=null`（Phase 6 由 Controller 注入） |
| Step 2 | [McpInspectionAgentServiceTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/service/McpInspectionAgentServiceTest.java) | 适配新构造器（4 参数），原 2 测试补充 Producer mock；新增 3 测试：① `generate_shouldDispatchInspectionReportMessageWithMappedFields` —— 验证 send 被调用 + 全字段映射（reportDate 幂等键 / toolRounds 审计 / siteIds ADMIN 全站点）；② `generate_mqFailure_shouldNotBlockResultReturn` —— AmqpException 被 catch，result 仍正常返回（ADR 0031 §6 不阻塞）；③ `generate_nullProducer_shouldSkipDispatchAndReturnResult` —— 模拟 test profile Producer=null，跳过投递不 NPE |

### 1.4 Phase 2 验收条件对照

| ADR 0031 要求 | 实现 | 验证 |
|---------------|------|------|
| §3.1 Agent 投递 InspectionReportMessage | `generate()` 末尾 `dispatchReport(result)` | 测试 ① verify send called |
| §3.1 Agent 不感知 SSE / Push Gateway | Producer 仅注入 RabbitTemplate（MQ 边界），Agent 不持有 emitter / userId→连接映射 | grep `SseEmitter\|PushGateway` in `McpInspectionAgentService.java` = 0 命中 |
| §6 RabbitMQ 异常行：catch + 降级日志，不阻塞主流程 | `dispatchReport()` try/catch AmqpException + log.warn | 测试 ② result 仍返回 |
| §6.1 消息可序列化 | InspectionReportMessage implements Serializable（Phase 1 已完成） | Jackson2JsonMessageConverter 已注入 |
| §5.4 ADMIN 全站点 siteIds 语义 | `toMessage()` 设 `siteIds=List.of()` | 测试 ① 验证 siteIds.isEmpty |
| §5.5 triggeredByUserId 仅审计用 | `toMessage()` 设 `null`（Phase 6 Controller 注入） | 测试 ① 注释说明 |

### 1.5 Phase 2 全量回归

- 主代码编译：`./mvnw -DskipTests compile` exit 0
- McpInspectionAgentServiceTest：5/5 pass（原 2 适配 + 新增 3）
- 全量测试：`./mvnw test` → **Tests run: 277, Failures: 0, Errors: 0, Skipped: 0**（274 + Phase 2 新增 3），BUILD SUCCESS，ApplicationContextLoadTest 未被破坏（@Nullable 注入兼容 test profile）

***

### 1.6 Phase 3 — Consumer 幂等 + 手动 ack（ADR 0031 §5.1 / §6）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Step 1 | [InspectionReportConsumer.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/InspectionReportConsumer.java) | `@Component @Profile("!test")` + `@RabbitListener(queues=INSPECTION_QUEUE, ackMode="MANUAL", concurrency="1-2")`；注入 `StringRedisTemplate`；`handleReport()` 流程：幂等检查 → 推送占位 → basicAck / 异常 basicNack(requeue=false)→DLQ；`isDuplicate()` 用 SETNX（`setIfAbsent(key, "1", Duration.ofHours(24))`）跨实例去重；ADMIN 全站点用单键 `inspection:{date}:all`，多站点按 siteId 逐个检查（任一首次即视为部分首次）；`dispatchToPushGateway()` 是 Phase 5 占位（日志 + TODO） |
| Step 2 | [InspectionReportConsumerTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/mq/InspectionReportConsumerTest.java) | 6 个测试：① ADMIN 全站点首次（SETNX true + ack）；② ADMIN 全站点重复（SETNX false + ack 不重复推送）；③ 多站点部分命中（site1 首次+site2 重复=ack）；④ 多站点全部命中（重复+ack）；⑤ Redis 故障（nack requeue=false → DLQ）；⑥ ack 抛 IOException 不冒泡（Consumer 内部 catch） |

### 1.7 Phase 3 验收条件对照

| ADR 0031 要求 | 实现 | 验证 |
|---------------|------|------|
| §5.1 Redis SETNX 跨实例幂等 | `isDuplicate()` 用 `setIfAbsent(key, "1", 24h)` | 测试 ①② 验证 true/false 路径 |
| §5.1 幂等键 `inspection:{reportDate}:{siteId}` | `idempotencyKey()` 拼接 | 测试 ① 验证键名 `inspection:2026-08-31:all` |
| §5.1 TTL 24h | `Duration.ofHours(24)` | 代码常量 `IDEMPOTENCY_TTL` |
| §5.4 ADMIN 全站点语义 | 空站点列表 → 单键 `:all` | 测试 ①② 验证 |
| §6 失败策略：nack(requeue=false)→DLQ | `nack()` 调 `basicNack(tag, false, false)` | 测试 ⑤ 验证 |
| §6 手动 ack | `ackMode="MANUAL"` + Channel 参数 | 测试 ① 验证 basicAck |
| Phase 5 推送预留 | `dispatchToPushGateway()` 日志占位 + TODO | 不影响幂等/ack 逻辑 |

### 1.8 Phase 3 全量回归

- 主代码编译：`./mvnw -DskipTests compile` exit 0
- InspectionReportConsumerTest：6/6 pass
- 全量测试：`./mvnw test` → **Tests run: 283, Failures: 0, Errors: 0, Skipped: 0**（277 + Phase 3 新增 6），BUILD SUCCESS

***

### 1.9 Phase 4 — Push Gateway + SseEmitterRegistry（ADR 0031 §5.2 / §5.3 / §6）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Step 1 | [SseEmitterSession.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/SseEmitterSession.java) | SSE 会话值对象（不可变），绑定 `userId` + `siteIds`（空=ADMIN 全站点）+ `SseEmitter` + `createdAt`；`canReceive(messageSiteIds)` 三段语义：本会话 ADMIN→true / 日报全站点→true / 交集非空→true |
| Step 2 | [SseEmitterRegistry.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/SseEmitterRegistry.java) | `@Component @Profile("!test")`，`ConcurrentHashMap<Long, SseEmitterSession>` 线程安全；`register(userId, siteIds)` 建连时调用，同 userId 覆盖旧会话（先 `closeQuietly` 再覆盖，防泄漏），emitter 设 30min timeout + `onCompletion/onTimeout/onError` 回调自动 `remove`；`findBySiteId(siteId)`/`findAdmins()`/`findAll()` 返回快照副本避免持锁遍历；`@PreDestroy shutdown()` 关闭全部 emitter + `clear()` |
| Step 3 | [InspectionPushGateway.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/InspectionPushGateway.java) | `@Component @Profile("!test")`，构造器注入 `SseEmitterRegistry`；`push(message)` 遍历 `findAll()` 快照，`session.canReceive(message.siteIds)` 匹配后 `emitter.send(SseEventBuilder)`；`sendSafely()` 捕获 `IOException`/`IllegalStateException` → `registry.remove(userId)` 移除失效会话 + 返回 false；单点失败不阻塞其他用户；无订阅时 INFO 日志不报错 |
| Step 4 | [InspectionReportConsumer.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/InspectionReportConsumer.java) | 构造器增 `@Nullable InspectionPushGateway` 第二参数（兼容 test profile PushGateway 不存在）；`dispatchToPushGateway()` 替换 Phase 3 日志占位 → null 时降级日志 + 非空时 `pushGateway.push(message)`；不包内层 try/catch（信任 gateway.push 不抛，未预期异常由 `handleReport` 外层 catch 兜底 nack→DLQ，ADR 0031 §6 一致语义）；Javadoc 更新为 Phase 4 接入语义 |
| Step 5 | [SseEmitterRegistryTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/mq/SseEmitterRegistryTest.java) | 15 个测试：register 基本/admin/null 归一化/同 userId 覆盖；findBySiteId 命中+无匹配；findAdmins 仅 ADMIN+无 ADMIN；findAll 快照副本隔离；remove 正常+不存在 userId 不抛；size；shutdown 清空+空 registry 不抛 |
| Step 6 | [InspectionPushGatewayTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/mq/InspectionPushGatewayTest.java) | 8 个测试：无会话跳过；站点匹配 send；不匹配跳过；ADMIN 接收全部；ADMIN 日报广播所有会话；IOException 移除；IllegalStateException 移除；混合会话单点失败不阻塞其他（Mock SseEmitter + Mock Registry + 真实 SseEmitterSession） |
| Step 7 | [InspectionReportConsumerTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/mq/InspectionReportConsumerTest.java) | 原 6 测试 setUp 改 2 参构造器（pushGateway=null 保持语义）；新增 2：① 首次消息 mock PushGateway → verify push 调用 + ack；② 重复消息 → verify push 不调用 + 仍 ack 防堆积 |

### 1.10 Phase 4 验收条件对照

| ADR 0031 要求 | 实现 | 验证 |
|---------------|------|------|
| §5.2 emitter 30min timeout | `SseEmitterRegistry.EMITTER_TIMEOUT_MS = 30*60*1000L`，`register` 内 `new SseEmitter(EMITTER_TIMEOUT_MS)` | 代码常量 + register 方法 |
| §5.2 emitter 泄漏防护（三重） | timeout + onCompletion/onTimeout/onError 回调自动 remove + `@PreDestroy shutdown()` 关闭全部 | RegistryTest shutdown 测试 + register 覆盖测试 |
| §5.3 单副本进程内直连路由 | `InspectionPushGateway.push` 遍历 `registry.findAll()` 快照，`canReceive` 匹配后 `emitter.send` | GatewayTest 匹配/不匹配/ADMIN 测试 |
| §5.3 失效 emitter 移除 | `sendSafely` 捕获 IOException/IllegalStateException → `registry.remove(userId)` | GatewayTest IOException/IllegalStateException 测试 |
| §5.3 单点失败不阻塞其他 | `sendSafely` 返回 false，循环继续 | GatewayTest 混合会话测试 |
| §5.4 ADMIN 全站点 siteIds 语义 | `SseEmitterSession.canReceive`：siteIds 空→true（接收所有） | GatewayTest adminSession 测试 + RegistryTest adminUser 测试 |
| §5.5 不信任 Consumer 传入 userId | `InspectionPushGateway` 只用 `registry.findAll()` 已绑定 userId/siteIds，不读 `message.triggeredByUserId` | gateway.push 代码无 triggeredByUserId 引用 |
| §6 RabbitMQ 异常行：不阻塞主流程 | gateway.push 内部已捕获单 emitter 异常，Consumer 不再包 try/catch；未预期异常由 `handleReport` 外层 catch nack→DLQ | ConsumerTest PushGateway 集成测试 |
| Phase 3 幂等/ack 逻辑不改动 | `isDuplicate`/`ack`/`nack` 未修改；仅 `dispatchToPushGateway` 替换占位 | ConsumerTest 原 6 测试保持 pass |
| test profile 兼容 | `@Nullable InspectionPushGateway` + `@Profile("!test")` 两个 bean 在 test profile 不注入，Consumer 构造器允许 null | ConsumerTest setUp pushGateway=null + 2 新测试用 mock |

### 1.11 Phase 4 全量回归

- 主代码编译：`./mvnw -DskipTests compile` exit 0
- SseEmitterRegistryTest：15/15 pass
- InspectionPushGatewayTest：8/8 pass
- InspectionReportConsumerTest：8/8 pass（原 6 适配 + 新增 2 PushGateway 集成）
- 全量测试：`./mvnw test` → **Tests run: 308, Failures: 0, Errors: 0, Skipped: 0**（283 + Phase 4 新增 25），BUILD SUCCESS，52s

***

### 1.12 Phase 6 — InspectionPushController（ADR 0031 §5.2 / §5.5 / §9）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Step 1 | [V14__push_sse_operation_types.sql](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/resources/db/migration/V14__push_sse_operation_types.sql) | Flyway V14：`chk_operation_type` 增 `PUSH`，`chk_target_type` 增 `SSE`（对齐 V12 模式 DROP CHECK + ADD CONSTRAINT） |
| Step 2 | [schema-h2.sql](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/resources/db/h2/schema-h2.sql) | H2 测试 schema 同步 V14 CHECK 约束（`PUSH` / `SSE`） |
| Step 3 | [OperationLog.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/annotation/OperationLog.java) | 注解 Javadoc `operationType` 增 `PUSH`、`targetType` 增 `SSE` |
| Step 4 | [InspectionPushController.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/controller/InspectionPushController.java) | `@RestController @RequestMapping("/api/push") @Profile("!test")`；`GET /inspection` produces=`text/event-stream` + `@RequireRole(VIEWER/OPERATOR/ADMIN)` + `@OperationLog(PUSH/SSE)` + `@Operation`；`subscribe()` 流程：`currentUserId(request)` 从 JWT Filter 注入的 attribute 读 userId → `siteAccessService.accessibleSiteIds(userId)` 解析站点 → 非 ADMIN 且空 List → `BusinessException(FORBIDDEN)` 403 拒绝（P0 安全防护）→ ADMIN(null) 传 `List.of()` / 非 ADMIN 有站点传实际 siteIds → `registry.register(userId, siteIds)` 返回 SseEmitter |
| Step 5 | [InspectionPushControllerTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/controller/InspectionPushControllerTest.java) | 3 个测试：① ADMIN（accessibleSiteIds=null）→ register(userId, List.of()) + 返回 emitter；② 非 ADMIN 有站点（siteIds=[10,20]）→ register(userId, siteIds) + 返回 emitter；③ 非 ADMIN 无站点（siteIds=空 List）→ 抛 BusinessException("无可访问站点") + 不调 register（P0 安全防护） |
| Step 6 | [FlywayProductionSeedIsolationTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/db/FlywayProductionSeedIsolationTest.java) | 迁移目录断言增 `V14__push_sse_operation_types.sql`（否则 V14 被当作非法迁移导致测试失败） |

### 1.13 Phase 6 验收条件对照

| ADR 0031 要求 | 实现 | 验证 |
|---------------|------|------|
| §5.2 SSE 端点 GET /api/push/inspection | `@GetMapping(value="/inspection", produces=TEXT_EVENT_STREAM_VALUE)` | Controller 代码 + Knife4j 文档 |
| §5.2 JWT Filter 鉴权 | `@RequireRole(VIEWER+)` → AuthInterceptor 校验；JwtAuthFilter 已注入 `request.setAttribute("userId", ...)` | 复用既有 Filter/Interceptor 链路，无新鉴权代码 |
| §5.2 userId → SiteAccessService.getAccessibleSiteIds | `siteAccessService.accessibleSiteIds(userId)` 解析；null=ADMIN 全站点 / 空=无权 / 非空=站点列表 | 测试 ①②③ 覆盖三种返回 |
| §5.5 禁止匿名建立 emitter | `@RequireRole` + JWT Filter 在 Controller 前执行 | AuthInterceptor 链路 |
| §5.5 跨站点隔离失败 = P0 | 非 ADMIN 无站点 → 403 拒绝（空 siteIds 在 SseEmitterSession 被当作 ADMIN 全站点 → 会导致收到所有日报） | 测试 ③ 验证抛异常 + 不调 register |
| §5.5 不信任 Consumer 传入 userId | Controller 只在建连时解析一次 userId + siteIds 绑定到 emitter；后续推送由 Push Gateway 按绑定 siteIds 路由 | gateway.push 不读 message.triggeredByUserId（Phase 4 已验证） |
| §9 Push 建连需审计 | `@OperationLog(PUSH/SSE)` + Flyway V14 CHECK 约束 | V14 迁移 + OperationLogAspect 在方法返回后写入 operation_log |
| §5.2 emitter 30min timeout | 由 `SseEmitterRegistry.register` 内部 `new SseEmitter(EMITTER_TIMEOUT_MS)` 设置 | Phase 4 Registry 已实现 + 测试 |

### 1.14 Phase 6 全量回归

- 主代码编译：`./mvnw -DskipTests compile` exit 0
- InspectionPushControllerTest：3/3 pass
- FlywayProductionSeedIsolationTest：通过（补 V14 后）
- 全量测试：`./mvnw test` → **Tests run: 311, Failures: 0, Errors: 0, Skipped: 0**（308 + Phase 6 新增 3），BUILD SUCCESS，53s

***

### 1.15 Phase 7 — 前端 EventSource 订阅 + nginx 反代缓冲关闭（ADR 0031 §5.2/§5.5/§6）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Step 1 | [JwtAuthFilter.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/security/JwtAuthFilter.java) | 新增 `resolveToken()` 私有方法 + `SSE_PATH_PREFIX="/api/push/"` / `SSE_TOKEN_PARAM="token"` 两个常量；解析顺序：①优先 `Authorization: Bearer` header（REST 主路径）；②仅当请求路径在 `/api/push/` 前缀下时 fallback 到 `?token=` query 参数（浏览器原生 EventSource 不支持自定义 header）；其他路径不支持 query fallback，避免 token 出现在 REST URL 被日志/Referer 泄漏；同时维持"header 优先于 query"的优先级，防止恶意 query 覆盖合法 header 攻击 |
| Step 2 | [JwtAuthFilterTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/security/JwtAuthFilterTest.java) | 新增 5 个 SSE 端点测试：① `sseEndpoint_validQueryToken_shouldPassAndInjectAttributes` — query token 有效 → 注入 userId/jti；② `sseEndpoint_invalidQueryToken_shouldReject401` — query token 无效 → 401（fail-close）；③ `sseEndpoint_noToken_shouldPassThrough` — 无 token 放行（由 @RequireRole 处理）；④ `nonSseEndpoint_queryTokenShouldBeIgnored` — REST 端点 `/api/devices?token=xxx` 不应读 query（用 `verifyNoInteractions` 验证 jwtUtils 不被调用）；⑤ `sseEndpoint_headerAndQueryPresent_headerWins` — header + query 同时存在时 header 优先（`verify never validateToken("query-tok-should-be-ignored")`） |
| Step 3 | [nginx.conf](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/deploy/nginx.conf) | 在 `/api/` 块之后新增 `location /api/push/` 块（nginx 前缀匹配按最长前缀选择，声明顺序无关）；关键配置：`proxy_buffering off` + `proxy_cache off`（禁用缓冲，否则 SSE 事件被 nginx 缓冲到块级再 flush → 浏览器日报"卡顿"）；`proxy_read_timeout 3600s` + `proxy_send_timeout 3600s`（≥ SseEmitterRegistry 30min timeout，否则 nginx 先于 Spring 关闭长连接）；`proxy_http_version 1.1` + `Connection ""`（禁用 upstream keepalive 复用，SSE 长连接需独立 TCP 不被连接池回收）；`access_log off`（?token= query 携带 JWT，关闭 access_log 避免 token 泄漏到磁盘日志，ADR 0031 §5.5 安全边界收口） |
| Step 4 | [InspectionReport.vue](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/views/InspectionReport.vue) | 新建前端 SSE 订阅页面（424 行）；`onMounted connect()` 建 `EventSource('/api/push/inspection?token=' + encodeURIComponent(jwt))`；`addEventListener('inspection-report', ...)` 监听后端 `SseEmitter.event().name("inspection-report")` 具名事件；JSON.parse 解析 InspectionReportMessage；按 `reportDate` 去重渲染（ADR 0031 §6 重复推送策略的前端兜底层）；最多保留 50 条，超出按时间倒序丢弃最旧；`onerror` 处理：readyState=2(CLOSED) 时手动 3s 后重试（浏览器原生自动重连仅对非 CLOSED 状态生效）；`onUnmounted source.close()` 防组件销毁后连接泄漏；三态 UI：`connecting`/`connected`/`reconnecting` + 圆点脉冲动画；日报卡片：reportDate + toolRounds/toolCalls/deviceCount/alarmCount/truncated 标签 + 工具调用次数 + 生成时间；report 正文等宽字体 pre-wrap 保留格式；3 个 EmptyState 空状态对应三态 |
| Step 5 | [router/index.js](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/router/index.js) | 增 `const InspectionReport = () => import('../views/InspectionReport.vue')` + `{ path: '/inspection', name: 'InspectionReport', component: InspectionReport }` 路由（无需 roles meta，VIEWER+ 均可访问，由后端 @RequireRole 校验） |
| Step 6 | [App.vue](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/App.vue) | Sidebar 增 `<el-menu-item index="/inspection"><el-icon><Notification /></el-icon><template #title>巡检日报</template>`（位于 AI 助手 之后，同属 AI 模块）；`titleMap` 增 `'/inspection': '巡检日报'`（面包屑显示） |
| Step 7 | [main.js](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/main.js) | 全局图标注册增 `Notification`（App.vue 用全局注册方式，不能只在 setup 内 import）；Calendar 图标在 InspectionReport.vue 内局部 import，无需全局注册 |

### 1.16 Phase 7 验收条件对照

| ADR 0031 要求 | 实现 | 验证 |
|---------------|------|------|
| §5.2 SSE 端点必须走 JWT Filter | `/api/push/` 路径下 JwtAuthFilter 支持 `?token=` query fallback | JwtAuthFilterTest ①②③ 验证 query token 三态 |
| §5.5 禁止匿名建立 emitter | `@RequireRole(VIEWER+)` 在 Controller 层拒绝无 token 请求（Phase 6 已实现） | InspectionPushControllerTest ③（Phase 6） |
| §5.5 不信任 Consumer 传入 userId | Controller 仅建连时绑定 userId/siteIds，前端只发 token 不发 userId | InspectionReport.vue 仅传 `?token=` |
| §6 Vue 断线 → EventSource 原生自动重连 | `onerror` 不主动 close（除非 readyState=2），让浏览器原生指数退避重连 | InspectionReport.vue onerror 逻辑 |
| §6 重复推送 → 前端按 reportDate 去重渲染 | `reports.value.some(r => r.reportDate === msg.reportDate)` 检测命中则 return 不渲染 | InspectionReport.vue addEventListener 回调 |
| §4.1 EventSource 原生断线重连优势落地 | 不引入第三方 SSE 库，纯 `new EventSource(url)` | InspectionReport.vue import 段无第三方依赖 |
| nginx 反代不缓冲 SSE | `proxy_buffering off` + `proxy_cache off` | nginx.conf location /api/push/ 块 |
| nginx read_timeout ≥ emitter timeout | 30min emitter timeout vs 3600s read_timeout | nginx.conf proxy_read_timeout 3600s |
| token 不泄漏到日志 | nginx `access_log off` + Filter 仅 /api/push/ 支持 query fallback | nginx.conf + JwtAuthFilterTest ④ |

### 1.17 Phase 7 全量回归

- 主代码编译：`./mvnw -DskipTests compile` exit 0
- JwtAuthFilterTest：11/11 pass（原 6 + 新增 5 SSE）
- 全量测试：`./mvnw test` → **Tests run: 316, Failures: 0, Errors: 0, Skipped: 0**（311 + Phase 7 新增 5），BUILD SUCCESS
- 前端构建：`npm run build` → ✓ built in 744ms，0 errors，`InspectionReport-s8r8a6gN.js` 3.71 kB / gzip 1.78 kB

***

## 2. 关键设计决策

### 2.1 为什么 inspection.queue 不设 TTL（与 alarm.queue 30s TTL 的差异）

- `alarm.queue` 的 30s TTL 适合「实时报警」场景：报警未在 30s 内消费 → 视为处理滞后 → 进 DLQ 升级；
- 巡检日报延迟敏感度低，Consumer 在短暂重启 / 慢处理时不应让消息过期进 DLQ；ADR 0031 §6.1 明确「消息 durable，broker 重启不丢」；
- DLX→DLQ 路由保留（处理失败仍进 DLQ 可人工重投），仅省略 TTL。

### 2.2 为什么 Phase 1 不修改 Agent（McpInspectionAgentService）

ADR 0031 §3.1 Agent 段职责：生成日报 + 投递 InspectionReportMessage。但用户 Phase 1 验收条件中「Agent 不感知 SSE」是**约束**而非接入要求 —— 最严格的「不感知 SSE」解读是 Agent 完全不接触推送链路。Phase 1 范围只覆盖 Step 1-3 + 单元测试，Agent 接入 Producer 留待后续阶段（Phase 2 之前的专门接入步骤或与 Consumer 同期），避免破坏 `McpInspectionAgentServiceTest` 现有 2 个测试。

### 2.3 siteIds 字段语义

- 空 List = 全站点（ADMIN 巡检语义，ADR 0031 §5.4）；
- 非空 List = Agent 巡检覆盖的站点集合，Consumer 按此路由；
- `setSiteIds(null)` 在 setter 内归一化为 `List.of()`，避免 Consumer NPE；
- `triggeredByUserId` **仅审计用**，Consumer/Push Gateway 不得据此越权路由（ADR 0031 §5.5：Push Gateway 只认 emitter 绑定 userId）。

***

## 3. 文档同步

- [AGENTS.md §3](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/AGENTS.md)：下一步改为「Day 85 Phase 2」，已完成模块追加「Day 85 Phase 1 投递侧」段；
- [Application-Architecture.md](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/docs/Architecture/Application-Architecture.md) §中间件整合 mq/ 行：追加 `InspectionReportMessage`/`InspectionReportProducer` 占位（标注 Phase 2-7 待实现）；
- [MQConfig.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/config/MQConfig.java) 类 Javadoc：架构图增 inspection 段 + 设计要点列表。
- **Phase 4 追加**：AGENTS.md §3「下一步」改为「Day 85 Phase 6 InspectionPushController」，已完成模块追加「Day 85 Phase 4 Push Gateway + SseEmitterRegistry」段；本日志增 §1.9-1.11（产出 + 验收对照 + 全量回归）；§4 待办 Step 5/8 标记完成。

***

## 4. Phase 4-7 待办（依据 ADR 0031 §3.1 / Preflight §8 实施顺序）

| 步骤 | 内容 | 依赖 |
|------|------|------|
| ~~Agent 接入~~ | ~~`McpInspectionAgentService.generate()` 末尾构造 Message + 调 Producer；MQ 异常 catch 降级日志，不阻塞主流程~~ | Phase 1 ✅ Phase 2 ✅ |
| ~~Step 4 Consumer~~ | ~~`InspectionReportConsumer`（`@RabbitListener` + 手动 ack + Redis SETNX 幂等 `inspection:{reportDate}:{siteId}/all` TTL 24h）~~ | Phase 2 ✅ Phase 3 ✅ |
| ~~Step 5 Push Gateway~~ | ~~`InspectionPushGateway` + `SseEmitterRegistry`（userId → emitter 表，30min timeout + 三重泄漏防护；接入 Consumer `dispatchToPushGateway()`）~~ | Phase 3 ✅ Phase 4 ✅ |
| Step 5 前置 | ~~`nginx.conf` 增 `location /api/push/ { proxy_buffering off; proxy_cache off; proxy_read_timeout 3600s; }`~~ | Step 6 ✅ Phase 7 |
| Step 6 | ~~`InspectionPushController`（`GET /api/push/inspection`，JWT + AuthInterceptor，返回 SseEmitter）~~ | Step 5 ✅ Phase 6 |
| Step 7 | ~~Vue EventSource 订阅 + 断线原生重连 + 渲染日报~~ | Step 6 ✅ Phase 7 |
| ~~Step 8 权限隔离测试~~ | ~~InspectionPushGatewayTest（站点匹配/ADMIN/失败移除/混合）+ InspectionReportConsumerTest（PushGateway 集成）~~ | Phase 4 ✅ |
| ~~Step 9~~ | ~~幂等测试（Redis SETNX 去重路径）~~ | Step 4 ✅ Phase 3 ✅ |
| Step 10 | 文档同步（Application-Architecture 推送链路完整图 + AGENTS §3 + Week13 复盘） | 全部 |

***

## 5. 风险与注意事项（继承自 ADR 0031 §8）

- **Agent 接入 Producer 时**：必须 catch AmqpException 后降级日志，不阻塞 Agent 主流程（ADR 0031 §6 RabbitMQ 异常行）；
- **Phase 2 Consumer 接入时**：必须手动 ack（与 AlarmConsumer 同模式），处理成功才 ack；失败 nack(requeue=false) → DLQ；
- **Phase 5 Push Gateway 接入时**：emitter 必须 timeout + 定时清理 + JVM shutdown hook，避免内存泄漏；
- **Phase 6 Controller 接入时**：SSE 端点必须走 JWT Filter，禁止匿名建立 emitter；
- **跨站点隔离失败 = P0 缺陷**（与 ADR 0020 站点作用域同等级别）。

***

> 完成时间：2026-08-31 22:00（Phase 1-3）/ 2026-09-01 17:00（Phase 4 Push Gateway）/ 2026-09-01 17:30（Phase 6 Push Controller）/ 2026-09-01 18:00（Phase 7 前端订阅 + nginx 反代）（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
