# Day 83 — Agent + MCP 联调：AI 自动巡检设备并生成日报

> **日期**：2026-08-30
> **阶段**：Phase 4 AI 集成 · Week 12（Agent + MCP）
> **分支**：`feat/agent-mcp`
> **配套 ADR**：[0029-mcp-client-auth-smoke.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0029-mcp-client-auth-smoke.md) / [0030-mcp-agent-inspection.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0030-mcp-agent-inspection.md)
> **验收结果**：✅ **GO**（Agent 经 MCP 客户端只读工具完成巡检日报；后端 269 tests 0 failures + 前端 build 0 errors）

---

## 一、今日产出

### 1.1 Day 82 缺口补齐（DG-001 / DG-002）

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 模板 | `.env.example` | CORS 段后补 `MCP_ACCESS_TOKEN` / `MCP_CLIENT_BASE_URL` / `MCP_CLIENT_SSE_ENDPOINT`（DG-001，ADR 0015 §8.3 对齐） |
| 审计 | `mcp/McpController.java` | `POST /api/mcp/smoke` 增加 `@OperationLog(operationType=MCP_SMOKE, targetType=MCP, {ret})`（DG-002） |
| 迁移 | `db/migration/V12__mcp_smoke_operation_types.sql` | `chk_operation_type` + `MCP_SMOKE`，`chk_target_type` + `MCP`；H2 `schema-h2.sql` 同步 |
| 测试 | `McpControllerAuditTest` / `OperationLogAspectTest` | 审计注解契约 + AOP 落库值断言（新增 2 用例） |

### 1.2 主任务：Agent + MCP 联调

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 客户端 | `mcp/McpClientService.java` | 新增 `openInspectionSession()`：一次巡检只建一个 SSE 连接，握手 + 读取工具清单；失败时关闭已建客户端 |
| 会话 | `mcp/McpInspectionSession.java` | `AutoCloseable` 巡检会话：持有 7 个 MCP 工具回调，记录去重设备数与告警数，`close()` 释放连接 |
| 适配器 | `mcp/McpToolCallbackAdapter.java` | MCP 工具清单 → Spring AI `ToolCallback`：名称/描述/输入 Schema 透传，调用转发 `McpSyncClient.callTool`，业务失败返回 `{"error":...}` |
| 服务 | `service/McpInspectionAgentService.java` | 复用 `ToolCallingAgent`：system prompt 引导先列设备、再逐台查基础信息/最近数据/告警，最后生成中文日报；6 轮硬限 |
| DTO | `dto/ai/AiInspectionReportResult.java` | 日期/日报文本/轮次/调用数/设备数/告警数/截断标记/工具 trace；`toString()` 紧凑摘要供审计 |
| 入口 | `controller/AiController.java` | `POST /api/ai/agents/inspection-report`，`@RequireRole(ADMIN)` + `@OperationLog(INSPECTION/MCP, {ret})` |
| 迁移 | `db/migration/V13__inspection_operation_type.sql` | `chk_operation_type` 扩展 `INSPECTION`（`target_type=MCP` 已由 V12 放行） |
| 测试 | `McpToolCallbackAdapterTest` / `McpInspectionSessionTest` / `McpInspectionAgentServiceTest` / `AiControllerInspectionAuditTest` / `McpClientInspectionSessionTest` | 4 + 2 + 2 + 1 + 1 用例（含 RANDOM_PORT 真连 SSE 会话） |

## 二、联调设计（ADR 0030）

- **单会话**：一次巡检建立单个 MCP SSE 会话并复用同一 `McpSyncClient`，工具清单
  一次性适配为 `ToolCallback[]`；会话结束统一释放连接，避免逐工具重连。
- **工具适配**：`McpToolCallbackAdapter` 直接从 MCP 工具清单透传名称/描述/JSON Schema，
  调用转发到 MCP Server；工具错误不中断 Agent，返回 `{"error":"..."}` 让模型如实收尾。
- **Agent 复用**：不另写循环，继续用 Day 79 的 `ToolCallingAgent`；巡检放宽到
  6 轮硬限（ADR 0026），并保留 `truncated` / 工具 trace 可观测性。
- **边界不变**：MCP 通道仍只读、无用户身份（ADR 0027 / ADR 0029）；触发巡检的
  REST 入口走标准 JWT + ADMIN，审计类型 `INSPECTION/MCP`。

## 三、测试与回归

```
McpClientInspectionSessionTest  1/1（RANDOM_PORT 真连 SSE，7 工具 + 复用连接）
McpToolCallbackAdapterTest      4/4（Schema 透传 / 参数转发 / 错误降级）
McpInspectionSessionTest        2/2（设备/告警指标、close 释放）
McpInspectionAgentServiceTest   2/2（生成链路、DeepSeek 未启用先失败）
AiControllerInspectionAuditTest 1/1（ADMIN + INSPECTION/MCP 审计契约）
McpControllerAuditTest          1/1（DG-002：MCP_SMOKE/MCP 契约）
OperationLogAspectTest          3/3（新增 MCP_SMOKE 落库断言）
FlywayProductionSeedIsolationTest 4/4（迁移清单扩展 V12/V13）
```

全量后端回归：`Tests run: 269, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
前端门禁：`vite build` 924ms，0 errors（本次无前端改动）。

> 测试数说明：Day82 基线 257 + DG-001/002 新增 2 + Day83 主任务新增 10 = 269。

## 四、明日计划（Day 84）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | 周复盘（Week 12）+ Agent/MCP 学习笔记更新 |
| ★★☆ | 巡检日报的展示/推送入口（Day 85+ 路线图内） |
| ★☆☆ | 同步 AGENTS / Application-Architecture 文档一致性 |
