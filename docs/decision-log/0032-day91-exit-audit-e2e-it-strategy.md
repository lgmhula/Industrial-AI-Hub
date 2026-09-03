# Decision 0032: Day 91 Exit Audit P0 修复 — E2E IT 策略与 SSE 回调验证边界

| 属性       | 值                                                                                              |
| -------- | ---------------------------------------------------------------------------------------------- |
| **状态**   | ✅ 已采纳（Day 91 Exit Audit P0-2/P0-5 修复落地）                                                            |
| **决策日期** | 2026-09-03                                                                                      |
| **决策者**  | hula0710 + AI 助手                                                                                |
| **关联**   | Day 91 Exit Audit / ADR 0018 测试隔离 / ADR 0031 推送链路 / AGENTS §4.2 测试隔离                            |

## 1. 背景

Day 91 Exit Audit 在 P0-2 / P0-5 / P1-1 三个维度发现关键测试真实性缺陷：

- **P0-2 E2E 真实贯通**：链路代码 7 段全部实现且实现正确（PASS），但 5 段链路**每段独立 mock**（InspectionReportConsumerTest mock StringRedisTemplate + Channel + PushGateway；InspectionPushGatewayTest mock SseEmitter + SseEmitterRegistry），**端到端从未在测试中跑通**；
- **P0-5 MQ 可靠性**：单实例代码层 PASS，但 ACK/Nack/DLQ/幂等全部基于 mock Channel；
- **P1-1 测试真实性**：「330/330 全绿」在统计意义上不等于「E2E 真的 E2E」，全仓无任何 AI/MQ/SSE 链路集成测试（仅有 DB migration IT + Redis 原语 IT）。

同时审计在 P0-4 SSE 稳定性维度发现：SseEmitterRegistryTest L31-33 **显式注释**「不测试 onCompletion/onTimeout/onError 自动移除，因为单测环境无法触发 Spring MVC 回调」—— 这正是 SSE 最关键的泄漏防护属性，从未被任何测试覆盖。

## 2. 决策

### 2.1 新增 `it` profile：补 E2E IT 而不污染默认测试基线

新增 `application-it.yml` + `InspectionPushChainIT.java`，使用 `@ActiveProfiles("it")` + `@EnabledIfEnvironmentVariable("RUN_INSPECTION_IT")`：

| 维度 | `test` profile（默认） | `it` profile（新增） |
|---|---|---|
| Redis autoconfig | 排除（无外部依赖） | 启用（连本地 compose.yml:6379） |
| RabbitMQ autoconfig | 排除 | 启用（连本地 compose.yml:5672） |
| Producer/Consumer/Registry/Gateway/Controller | @Profile("!test") 不实例化 | 全部实例化 |
| 默认 `./mvnw test` 是否执行 | 是 | **否**（@EnabledIfEnvironmentVariable 保护） |
| 用途 | 343/343 单元测试基线 | 显式 `RUN_INSPECTION_IT=true ./mvnw test -Dtest=InspectionPushChainIT` |

**理由**：
- 不引入新依赖（Testcontainers 已有但 RabbitMQ/Redis module 未引入，按 AGENTS §5 不升级依赖）；
- 复用本地 docker-compose 基础设施（compose.yml 已配齐 RabbitMQ:5672 + Redis:6379）；
- 仿照现有 RedisContainerIT / MySqlMigrationV7IT 的 `@EnabledIfEnvironmentVariable` 模式，保持 IT 风格一致；
- 凭证从 .env 加载（`set -a && . ./.env && set +a`），不污染 test profile。

### 2.2 IT 覆盖范围（3 个测试用例）

`InspectionPushChainIT.java` 覆盖：

| 用例 | 链段 | 验证内容 |
|---|---|---|
| `producerSend_consumerProcesses_redisIdempotencyKeySet` | Producer→MQ→Consumer→Redis | Producer.send() 真实投递 → Consumer @RabbitListener 真实处理 → Redis SETNX 真实写入幂等键 `inspection:{reportDate}:all` |
| `duplicateMessage_consumerSkipsPush_idempotencyKeyTtlUnchanged` | Consumer 幂等性 | 第二次发送相同消息，Consumer 命中幂等键跳过推送，TTL 不重置（验证 setIfAbsent 不覆盖现有键） |
| `consumerFailure_routesToDLQ` | Consumer 失败→DLQ | 投递非法 payload 触发反序列化失败 → basicNack(requeue=false) → inspection.dlx → inspection.dlq |

### 2.3 不验证的部分（明确边界）

| 不覆盖项 | 原因 | 留作何时覆盖 |
|---|---|---|
| **P0-4 SSE 回调自动移除（onCompletion/onTimeout/onError）** | Spring 的 SseEmitter 设计依赖真实 Web 异步上下文（WebAsyncManager 注入的 Handler）触发回调；`emitter.complete()` 在无 HTTP 上下文时是 no-op（Day 91 Exit Audit 实测：emitter.complete() 后 registry.size() 不变）。MockMvc 的 asyncDispatch 可触发单次回调，但无法覆盖 SSE 长连接多次事件流 + 真实断连场景 | Phase 5 真实浏览器 E2E（Selenium/Playwright）或 MockMvc 异步分发专项 IT |
| DeepSeek AI 真实调用 | 避免第三方 API 依赖 + 账单 | 已有 DeepSeekClientTest 单测覆盖 503/降级 |
| SSE 网络层 HTTP 长连接事件接收 | 标准 JUnit 难以测长连接 | 同 P0-4，Phase 5 浏览器 E2E |
| JWT 鉴权链路 | 已有 JwtAuthFilterTest 覆盖 | 不重复 |

## 3. 验证结果

执行 `RUN_INSPECTION_IT=true ./mvnw test -Dtest=InspectionPushChainIT`（连本地 docker-compose）：

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.271 s -- in dev.reboot.mq.InspectionPushChainIT
[INFO] BUILD SUCCESS
```

3/3 全绿。默认 `./mvnw test` 仍 343/343 全绿（@EnabledIfEnvironmentVariable 保护）。

## 4. 风险与边界

### 4.1 P0-4 SSE 回调无法在 IT 覆盖

**事实**：SseEmitterRegistry.register() 中 `emitter.onCompletion(() -> remove(userId))` 这行代码的正确性依赖运行时 Spring MVC 框架行为，无法用 JUnit IT 验证（Day 91 Exit Audit 实测 emitter.complete() 在无 Web 上下文时不触发回调）。

**缓解**：
1. 代码层注释已明确标注回调依赖 Spring MVC 框架；
2. PushGateway.sendSafely 兜底：emitter.send() 抛 IOException/IllegalStateException 时主动调 registry.remove，不依赖回调；
3. @PreDestroy shutdown() 关闭全部 emitter，进程退出时兜底清理；
4. 30min SseEmitter timeout 是硬上限，即使回调全失效，连接也会在 30min 后强制过期。

**遗留风险**：单实例 + 无浏览器 E2E 时，无法验证"emitter 异常断开后 onCompletion 回调真的被调用"。Phase 5 浏览器 E2E 落地前，依赖 sendSafely + timeout 双兜底。

### 4.2 多副本风险仍未解决（P1-2）

本 ADR 仅修复 P0-2/P0-5/P1-1，**不解决** P1-2 多副本风险（SseEmitterRegistry 进程内、inspectionQueue 是 work-queue、AiRateLimitInterceptor per-JVM、SimpleVectorStore 内存）。多副本改造留作 Phase 5 启动前专项任务（Redis pub/sub 桥接 + fanout queue + Redis 限流 + Qdrant）。

## 5. 关联修改

- `backend/src/test/resources/application-it.yml`（新增）：`it` profile 配置，H2 + 真实 RabbitMQ + 真实 Redis
- `backend/src/test/java/dev/reboot/mq/InspectionPushChainIT.java`（新增）：3 个 E2E IT 用例
- `AGENTS.md` §3：新增「Day 91 Exit Audit P0 修复」条目
- `docs/Architecture/Application-Architecture.md` Based on：补 Day 91 Exit Audit
- `backend/DAILY/Day91-Exit-Audit.md`（新增）：审计与修复日志

## 6. 后续行动

- **Phase 5 启动前**（Day 92 之前）：用 Selenium/Playwright 写真实浏览器 E2E，覆盖 P0-4 SSE 回调
- **多副本改造**：按 P1-2 修复矩阵做 Redis pub/sub 桥 + fanout queue + Redis 限流 + Qdrant
- **CI 集成**：在 GitHub Actions 中加入 `RUN_INSPECTION_IT=true` 的 IT 步骤（用 docker-compose 启动 RabbitMQ+Redis service container）
