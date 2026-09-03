# Day 91 Exit Audit — Phase 4 收官后置审计 + P0 修复

> **状态**：Day 91 Exit Audit 完成（P0-1 Git 修复 + P0-2/P0-5 E2E IT 落地 + P0-4 SSE 回调边界明确）
> **关联**：Day 91 Phase 4 收官（Week14.md + Git tag v2.0-ai）+ AGENTS §4.3 执行后清单
> **分支**：`feat/exit-audit-p0-fix`（基于 main `b584173` 即 Day 91 合并 commit）
> **决策记录**：ADR 0032 Day 91 Exit Audit E2E IT 策略与边界

---

## 一、审计触发

Day 91 Phase 4 收官后，按用户审计需求矩阵执行 Read-Only Exit Audit，覆盖 P0×5 + P1×5 共 10 个维度，回答「E2E 到底是不是真的 E2E」。

## 二、审计发现汇总

| 优先级 | 项 | 结论 | 关键证据 |
|---|---|---|---|
| **P0** | Git/分支完整性 | **FAIL** | main = 7813b55（Day 066-085），v2.0-ai tag 在 0fbccfe（feat 分支，不可从 main 到达）；5 个 Day86-91 堆叠分支未合并 |
| **P0** | E2E 链路代码完整性 | **PASS** | 7 段链路代码全实现且实际连线 |
| **P0** | E2E 真实贯通 | **PARTIAL FAIL** | 链路代码 ✅，但 5 段全 mock，端到端从未在测试中跑通；无任何 IT |
| **P0** | 站点隔离 | **PASS** | 非 ADMIN + 空 siteIds 在 Controller 即 403 |
| **P0** | SSE 稳定性 | **PARTIAL** | 代码完整（30min timeout + 3 回调 + @PreDestroy），关键回调属性从未测过 |
| **P0** | MQ 可靠性（单实例） | **PASS** | ackMode=MANUAL + Redis SETNX 跨实例 + DLX/DLQ 声明 |
| **P1** | 测试真实性 | **FAIL** | InspectionReportConsumerTest 全 mock Channel/Redis/PushGateway；SseEmitterRegistryTest 显式跳过回调测试 |
| **P1** | 多副本风险 | **CRITICAL HIDDEN RISK** | SseEmitterRegistry 进程内 + inspectionQueue work-queue + AiRateLimitInterceptor per-JVM + SimpleVectorStore 内存 |
| **P1** | 安全审计 | **PASS** | JWT/EventSource token/MCP token/操作日志全合规 |
| **P1** | 资源/性能泄漏 | **PASS（单实例）** | SseEmitter/MQ Channel/McpClient 都有清理逻辑；线程池/连接池用默认值 |
| **P1** | 文档与代码一致性 | **MINOR INCONSISTENCY** | ADR-0031 ✅；Application-Architecture "Based on" 仅到 Day 87；AGENTS §3 v2.0-ai 声称在 main 但实际在分支 |

## 三、修复矩阵执行

### 3.1 P0-1 Git 修复（已落地）

**操作**：5 个堆叠 feature 分支通过 `--no-ff` 一次性合并到 main

```
main b584173 (Day 86-91 Phase 4 收官合并到 main (governance bootstrap)) ← merge commit + v2.0-ai tag
├─ 0fbccfe Day 91: Phase 4 收官
├─ ad2a86c Day 90: AI 模块集成 Runbook
├─ bf4c931 Day 89: AI 模块重构
├─ 33f30cc Day 87-88: 前端 4 页 + Week13 + 架构图 V2.5
├─ 92050a3 Day 86: AI 巡检自动报警
└─ 7813b55 Day 066-085: Phase 4 squash（之前的 main HEAD）
```

- 丢弃 Week14.md 脏修改（仅 Markdown 表格自动格式化，无内容损失）
- 旧 v2.0-ai tag（在 0fbccfe）删除，在 main merge commit `b584173` 重打 annotated tag
- 5 个本地 feature 分支删除
- main + tag 已 push 到 origin

### 3.2 P0-2 / P0-5 E2E IT 落地（ADR 0032）

**新增文件**：
- `backend/src/test/resources/application-it.yml`：`it` profile 配置（H2 + 真实 RabbitMQ + 真实 Redis + 排除 Redisson/Spring AI autoconfig）
- `backend/src/test/java/dev/reboot/mq/InspectionPushChainIT.java`：3 个 E2E IT 用例

**IT 用例**：
1. `producerSend_consumerProcesses_redisIdempotencyKeySet` — Producer→MQ→Consumer→Redis SETNX 真实流转
2. `duplicateMessage_consumerSkipsPush_idempotencyKeyTtlUnchanged` — 重复消费跳过 + TTL 不重置
3. `consumerFailure_routesToDLQ` — 失败→basicNack→inspection.dlx→inspection.dlq

**执行结果**：
```
$ set -a && . ./.env && set +a
$ cd backend
$ RUN_INSPECTION_IT=true ./mvnw test -Dtest=InspectionPushChainIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.271 s
[INFO] BUILD SUCCESS
```

默认 `./mvnw test` 仍 **343/343 全绿**（@EnabledIfEnvironmentVariable 保护，IT 不污染基线）。

### 3.3 P0-4 SSE 回调边界（明确不覆盖）

**事实**：Spring 的 `SseEmitter.complete()` 在无 Web 异步上下文（无 `WebAsyncManager` 注入的 Handler）时是 no-op。Day 91 Exit Audit 实测：

```java
SseEmitter emitter = registry.register(userId, List.of());
assertThat(registry.size()).isEqualTo(1);  // PASS
emitter.complete();
assertThat(registry.size()).isEqualTo(0);  // FAIL — 仍是 1
```

`onCompletion` 回调未触发。这是 Spring MVC 框架设计依赖，非项目代码缺陷。

**缓解策略**（ADR 0032 §4.1）：
1. `PushGateway.sendSafely` 兜底：emitter.send() 抛异常时主动 `registry.remove`
2. `@PreDestroy shutdown()` 进程退出时关闭全部 emitter
3. 30min SseEmitter timeout 硬上限，回调全失效也会强制过期

**遗留**：Phase 5 启动前用 Selenium/Playwright 真实浏览器 E2E 覆盖（断连/超时场景）。

### 3.4 P1-2 多副本风险（未收口，Phase 5 启动前专项）

| 风险 | 当前状态 | Phase 5 改造方案 |
|---|---|---|
| SseEmitterRegistry 进程内 | 已在 SseEmitterRegistry.java:13-18 注释标注 | Redis pub/sub 桥接 |
| inspectionQueue work-queue 语义 | N 副本只有 1 个 consumer 拿消息 | 改 fanout 或 PushGateway 订阅 Redis pub/sub |
| AiRateLimitInterceptor per-JVM | 3 副本 = 3× 配置速率 | Redis 计数限流 |
| SimpleVectorStore 内存 | 每副本向量索引不一致 | Qdrant 替换（ADR 0024 已承认） |

## 四、未执行的修复矩阵项

| 项 | 状态 | 说明 |
|---|---|---|
| P1-1 mock 清理 | 不需要单独做 | IT 落地后现有 mock 单测保留作边界用例 |
| P1-5 文档同步 | ✅ 完成 | AGENTS §3 + Application-Architecture Based on + 本日志 + ADR 0032 |

## 五、明日计划

1. PR `feat/exit-audit-p0-fix` → main（squash 合并 + 删除分支）
2. Phase 5 Day 92 PLC 基础概念（Modbus / 寄存器 / 线圈 / 离散输入 / 输入寄存器 4 类数据），对齐 DAILY_ROADMAP L568
3. Phase 5 启动前补 Selenium/Playwright 浏览器 E2E（覆盖 P0-4 SSE 回调）—— 可与 Day 92-94 PLC 概念并行

## 六、关键交付物

- `docs/decision-log/0032-day91-exit-audit-e2e-it-strategy.md`（新增 ADR）
- `backend/src/test/resources/application-it.yml`（新增 it profile）
- `backend/src/test/java/dev/reboot/mq/InspectionPushChainIT.java`（3 个 E2E IT）
- `AGENTS.md` §2 文档索引扩 0031-0032 / §3 加 Exit Audit P0 修复条目
- `docs/Architecture/Application-Architecture.md` Based on 补 Day 88-91 + Exit Audit
- main `b584173` + `v2.0-ai` tag 在 main 上重打
