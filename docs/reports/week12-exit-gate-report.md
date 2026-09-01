# Week12 Exit Gate Report

> Day85 开工前 Gate 阶段任务产物。
> 目标：修复 Week12 Exit Review 的 P1 + 固化 Day85 架构边界 + Git 卫生分析 + 建立开发基线。
> **本报告不含 Day85 业务代码实现。**

| 字段 | 值 |
|------|-----|
| 生成时间 | 2026-08-31 21:15 (Asia/Shanghai) |
| 执行分支 | `docs/week12-review` |
| HEAD（执行前） | `f0311bb` Day 082 |
| 关联文档 | Week12 Exit Review / ADR 0026-0031 / Week12.md |

## 1. Git 状态

执行前基线（Step 1 只读采集）：

| 项 | 值 |
|----|----|
| branch | `docs/week12-review` |
| HEAD | `f0311bb` Day 082: MCP 客户端集成与传输鉴权冒烟（ADR 0029） |
| 已修改（M） | 14 文件（.env.example, AGENTS.md, DAILY_ROADMAP, OperationLog.java, AiController.java, McpClientService.java, McpController.java, OperationLogAspectTest.java, FlywayProductionSeedIsolationTest.java, schema-h2.sql, Application-Architecture.md, agent-learning-notes.md, mcp-learning-notes.md, ADR 0029） |
| 未跟踪（??） | 17 文件（Day83.md, Day84.md, Week12.md, AiInspectionReportResult.java, McpInspectionSession.java, McpToolCallbackAdapter.java, McpInspectionAgentService.java, V12, V13, 6 测试类, ADR 0030, controller 测试目录, 杂散 AI 产物） |
| 同 HEAD 分支 | `feat/agent-mcp`（有 origin 追踪，是 Day83 代码的合理归宿） |

本次 Gate 任务执行后**新增**修改/文件：
- M `backend/src/main/java/dev/reboot/aop/OperationLogAspect.java`（P1-1 修复）
- M `backend/src/test/java/dev/reboot/aop/OperationLogAspectTest.java`（新增 ApiResponse 包装测试）
- ?? `docs/decision-log/0031-day85-ai-report-push-architecture.md`（ADR-0031）
- ?? `docs/reports/week12-exit-gate-report.md`（本报告）

**Git 卫生分析（Step 4，仅分析不执行）**：
当前 `docs/week12-review` 分支承载了 Day83 全部代码改动（McpInspectionAgentService / McpToolCallbackAdapter / V12 / V13 / 测试等），与 `docs/*` 仅文档的语义冲突，违反 AGENTS §4.4。
建议：Day83 代码应归 `feat/ai-inspection` 或并入既有 `feat/agent-mcp`（同 HEAD 有 origin），Day84 文档（Day83.md / Day84.md / Week12.md / 笔记）留在 `docs/week12-review`。
本次未自动移动 commit，留待人工治理决策。

## 2. P1 修复结果

### P1-1：OperationLog `{ret}` ApiResponse 包装失效

**根因（独立静态验证确认）**：
`OperationLogAspect.formatResult()` 对 `result.toString()` 直接求值，生产中 Controller 返回 `ApiResponse<T>`，而 `ApiResponse`（`dev.reboot.dto.ApiResponse`）**未覆写 toString()**，必然产出 `ApiResponse@hash`，丢失 `AiInspectionReportResult` / `AiDeviceStatusResult` 设计好的紧凑摘要。
原 `OperationLogAspectTest` 用 `when(joinPoint.proceed()).thenReturn(deviceStatusResult())` 直接返回**未包装**结果，与生产 `ApiResponse.ok(...)` 脱节，导致 bug 漏网。

**修复**（[OperationLogAspect.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/aop/OperationLogAspect.java#L149-L160) `formatResult`）：
```java
if (result instanceof ApiResponse<?> response && response.getData() != null) {
    result = response.getData();
}
String text = result instanceof String s ? s : result.toString();
```
向后兼容：非 ApiResponse 走原路径；ApiResponse.data 为 null 时退回 ApiResponse.toString() 不崩溃。

**测试补强**（[OperationLogAspectTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/aop/OperationLogAspectTest.java#L168-L204)）：
新增 `around_withApiResponseWrappedResult_shouldUnwrapDataForDescription`，用 `ApiResponse.ok(inspectionReportResult())` 模拟生产签名，断言 description 不含 `ApiResponse@` 且含 `rounds=6 calls=66 devices=20 alarms=2 truncated=true` 五要素。

**运行时验证**（Step 7，真实 DeepSeek + 真实 MCP SSE 链路）：
| 修复前（Exit Review id=148-150） | 修复后（Gate id=153） |
|----|----|
| `AI 设备巡检日报（MCP 工具调用） dev.reboot.dto.ApiResponse@6f57d18a` | `AI 设备巡检日报（MCP 工具调用） AiInspectionReportResult{date=2026-08-31, rounds=5, calls=61, devices=20, alarms=2, truncated=false}` |

HTTP 200，29.7s 完成，DB id=153 行 description 已是正确业务摘要，无 `ApiResponse@hash`。**P1-1 端到端修复成立。**

## 3. ADR-0031 结果

文件：[0031-day85-ai-report-push-architecture.md](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/docs/decision-log/0031-day85-ai-report-push-architecture.md)

**路径偏离说明**：任务原文要求 `docs/adr/0031-...`，但项目约定（AGENTS §2 文档索引 + ADR 0026-0030 现存位置）统一为 `docs/decision-log/`。AGENTS.md 是唯一对齐锚点，遵循项目约定放置，文件名遵循 `0031-` 序号 + kebab 描述。

**架构边界冻结**：
```
AI Agent → RabbitMQ → Report Consumer → Push Gateway → SSE/WebSocket → Vue Client
```

**关键决策**：
| 议题 | 决策 |
|------|------|
| 禁止架构 | RabbitMQ → Browser 直连（broker 非浏览器 consumer，凭证/协议不匹配） |
| 传输选型 | **SSE（SseEmitter）** — 单向推送、EventSource 原生断线重连、HTTP 穿透 Nginx、Spring MVC 原生无新依赖 |
| WebSocket 升级时机 | 需双向控制面（重巡检/过滤）时单开 `/ws` 通道，不污染 SSE 日报通道 |
| 鉴权 | SSE 端点走 JWT Filter + AuthInterceptor，复用 `SiteAccessService`（ADR 0020） |
| 站点隔离 | emitter 建连时绑定 userId → siteIds；Consumer 不越权指定 userId；跨站点隔离失败 = P0 |
| 多副本路由 | Day85 单副本进程内直连；生产前切 Redis pub/sub（`inspection:site:{siteId}` channel） |
| 失败策略 | Vue 断线原生重连 / Consumer DLQ+retry / Redis 降级本地直发 / RabbitMQ 异步不阻塞 Agent / 幂等键 `inspection:{reportDate}:{siteId}` Redis SETNX 24h |
| 不在 Day85 范围 | MQTT/PLC 推送、WebSocket 双向、OAuth 多租户、Kafka 替换 RabbitMQ |

**修正了 ROADMAP Day85 原文「RabbitMQ → 前端」的架构表述缺陷**：RabbitMQ 只承担 Agent→Consumer 段，不能承担 Consumer→浏览器段。

## 4. 测试结果

| 指标 | 值 |
|----|----|
| Tests run | **270** |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| BUILD | SUCCESS |
| 基线对比 | 269 → 270（+1 新增 ApiResponse 包装测试，无回归） |
| OperationLogAspectTest | 4/4 绿（原 3 + 新 1） |

测试日志中 DeepSeek HTTP 401 为 test profile 占位 key（`test-deepseek-key-not-used`）预期行为，非失败；`Surefire is going to kill self fork JVM` 为 surefire 清理告警，非测试失败。

## 5. Day85 进入条件

| 条件 | 状态 | 证据 |
|------|------|------|
| P1-1 {ret} 修复 | ✅ | 代码 + 单元测试 + 运行时 DB id=153 |
| ApiResponse 包装测试新增 | ✅ | `around_withApiResponseWrappedResult_shouldUnwrapDataForDescription` |
| operation_log 不再出现 ApiResponse@ | ✅ | DB id=153 description 实证 |
| ADR-0031 完成 | ✅ | `docs/decision-log/0031-...` |
| Day85 架构边界明确 | ✅ | 6 段分层 + SSE 选型 + 站点隔离 + 失败策略 |
| Maven 测试通过 | ✅ | 270/0/0/0 |
| 未实现 Day85 业务代码 | ✅ | 仅改 aspect + 测试 + ADR + 报告，无 SSE/MQ/Push/Vue 代码 |
| Git 状态记录完成 | ✅ | Step 1 基线 + 本次增量已记录 |

**遗留（非阻塞，P2，由任务限定为仅分析）**：
- 分支命名治理：`docs/week12-review` 含 Day83 代码，建议归 `feat/agent-mcp`（分析未执行）
- AI 产物清理：`docs/reports/Industrial-AI-Hub-对话总结与项目理解-20260831.md` 疑似中间产物，建议删除或移 `docs/archive/ai-notes/`（分析未执行）

## 6. 最终结论

# **PASS**

**理由**：
- Week12 Exit Review 的 P1-1 已修复并经单元 + 运行时双重验证；
- ADR-0031 已冻结 Day85 六段推送架构边界，修正 ROADMAP 表述缺陷；
- 测试基线 270/0/0/0，未降低 269 baseline；
- 未实现任何 Day85 业务代码（SSE/WebSocket/Push Gateway/Vue 推送均未触碰）；
- Git 卫生与 AI 产物为 P2 遗留，任务范围限定为仅分析，已记录建议，不阻塞 Day85。

**Day85 可安全开工**，依据 ADR-0031 的六段分层与 SSE 选型实施。

---

## 验收清单

- [x] OperationLog {ret} 已修复
- [x] ApiResponse 包装测试新增
- [x] operation_log 不再出现 ApiResponse@
- [x] ADR-0031 完成
- [x] Day85 架构边界明确
- [x] Maven 测试通过
- [x] 未实现 Day85 业务代码
- [x] Git 状态记录完成

---

> 维护者：AI 助手 + hula0710 | 路径遵循 AGENTS §2 项目约定（`docs/decision-log/` + `docs/reports/`）
