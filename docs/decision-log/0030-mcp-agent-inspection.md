# Decision 0030: Agent + MCP 联调：设备巡检日报（Week 12）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-30 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 83 / ADR 0026 / ADR 0027 / ADR 0029 / ToolCallingAgent / McpClientService |

## 1. 背景

Day 82 已打通 MCP 客户端（ADR 0029）：SSE 握手、工具清单、只读探针与
`X-MCP-Token` 传输鉴权。Day 83 路线图要求真正让 Agent 通过 MCP 客户端工具自动
巡检设备并生成日报，而不是只做冒烟。

联调需要解决三个问题：MCP Server 返回的是 `McpSchema.Tool` 工具清单，而 Spring AI
Agent 消费的是 `ToolCallback`；一次巡检可能调用 10+ 次工具，不能逐次重连 SSE；
巡检入口需要 ADMIN 审计闭环，同时不破坏 MCP 通道“只读、无用户身份”的既定边界。

## 2. 决策

### 2.1 一次巡检一个 MCP SSE 会话

`McpClientService` 新增 `openInspectionSession()`：建立连接 → `initialize()` 握手 →
`listTools()` 读取工具清单，封装为 `McpInspectionSession`（`AutoCloseable`）。
Agent 在会话内复用同一个 `McpSyncClient` 多次调用工具，结束后 `close()` 统一释放
连接；握手/清单读取失败时也会关闭已建客户端，避免连接泄漏。

### 2.2 MCP 工具清单 → ToolCallback 适配器

`McpToolCallbackAdapter` 把 `McpSchema.Tool` 适配为 Spring AI `ToolCallback`：

- `getToolDefinition()` 透传 MCP 工具名、描述与 JSON Schema，模型可零配置调用。
- `call()` 把模型生成的 JSON 参数转发到 `McpSyncClient.callTool`，文本结果原样返回。
- MCP 工具返回 `isError` 或调用抛异常时，降级为 `{"error":"..."}` JSON，不中断
  Agent 循环，让模型在日报中如实说明受限项。

### 2.3 复用 ToolCallingAgent，不重复造循环

`McpInspectionAgentService` 沿用 Day 79 的通用 `ToolCallingAgent`，只提供
system prompt（先列设备 → 逐台查基础/数据/告警 → 生成中文日报）与 6 轮硬限。
会话同时统计去重设备数与告警数，随 `AiInspectionReportResult` 返回，供结果展示
与审计摘要使用；`truncated` 标记是否触达轮次硬限。

### 2.4 ADMIN 入口 + INSPECTION 审计

新增 `POST /api/ai/agents/inspection-report`，`@RequireRole(ADMIN)`（巡检全量读取
设备，不开给 VIEWER/OPERATOR），`@OperationLog(operationType=INSPECTION,
targetType=MCP, {ret})`。Flyway V13 扩展 `chk_operation_type` 放行 `INSPECTION`
（`target_type=MCP` 已由 V12 放行）。

### 2.5 安全边界不变

MCP 通道仍保持只读、无用户身份（ADR 0027 / ADR 0029），Agent 巡检权限收敛在
JWT REST 层：只有 ADMIN 能触发全量巡检。MCP 工具本身不新增任何写能力或身份参数。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| 每次工具调用新建 MCP 连接 | 10+ 次调用会重复握手/建立 SSE，延迟与失败面都放大；单会话复用更接近真实客户端 |
| 为巡检另写专用 Agent 循环 | 与 ToolCallingAgent 职责重复，轮次治理/工具 trace 需要双份维护 |
| 向 MCP 工具透传用户身份并做站点授权 | MCP 1.0 无标准身份头，ADR 0029 已判定等待协议演进；巡检权限放到 REST 入口更可控 |

## 4. 影响与验证

- 代码：`McpClientService` / 新增 `McpInspectionSession` / `McpToolCallbackAdapter` /
  `McpInspectionAgentService` / `AiInspectionReportResult`；`AiController` 新增
  inspection-report 端点；Flyway V13 + H2 schema 同步。
- 测试：`McpClientInspectionSessionTest` 1（RANDOM_PORT 真连 SSE，7 工具 + 复用
  连接）+ `McpToolCallbackAdapterTest` 4 + `McpInspectionSessionTest` 2 +
  `McpInspectionAgentServiceTest` 2 + `AiControllerInspectionAuditTest` 1。
- 回归：全量后端 `Tests run: 269, Failures: 0, Errors: 0, Skipped: 0`
  （Day82 基线 257 + DG 2 + Day83 主任务 10）；前端 `vite build` 924ms 0 errors。
- 文档：Day83 日志、DAILY_ROADMAP、AGENTS、Application-Architecture 同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| 6 轮硬限内无法逐台巡检大量设备 | prompt 要求超过 10 台时优先离线/告警设备并注明未逐台覆盖，结果带 `truncated` 标记 |
| MCP Server 不可用导致巡检失败 | 失败转 `SERVICE_UNAVAILABLE` 并关闭会话；`POST /api/mcp/smoke` 可先行诊断链路 |
| 工具返回数据过大 | MCP 工具已有 `limit` clamp（ADR 0028），适配器只透传文本内容 |
| operation_log CHECK 再次漂移 | Flyway V12/V13 + H2 schema 同步，契约测试锁定 MCP_SMOKE / INSPECTION |

---

> 最后更新：2026-08-30 | 维护者：AI 助手 + hula0710
