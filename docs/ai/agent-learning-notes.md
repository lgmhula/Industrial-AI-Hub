# Week 12 Agent 学习笔记：ReAct、通用循环与 MCP 联调

> 日期：2026-08-30 | 覆盖：Day 78-83（ADR 0026 / ADR 0030）

---

## 1. Agent 是什么

普通 AI 接口是“输入 → 一次补全 → 输出”。Agent 则把模型放到一个循环里：

```text
用户目标 → 模型思考（要调哪个工具）→ 执行工具 → 观察结果 → 继续思考 → 最终回答
```

这就是 ReAct（Reasoning + Acting）。模型不再只是“说话”，而是能“行动”。

## 2. 与单次 Function Calling 的区别

| | 单次 Function Calling | Agent |
|---|---|---|
| 工具调用 | 通常一次 | 多轮、多工具 |
| 上下文 | 静态 | 工具结果动态回填 |
| 终止条件 | 模型自然结束 | 轮次硬限 + 模型自然结束 |
| 可观测性 | 调用数 | 轮次/调用数/轨迹/截断 |

## 3. 循环伪代码

```text
conversation = [system, user]
rounds = 0
while true:
    response = model.call(conversation + tools)
    if no tool calls:
        return answer(response)
    if rounds >= MAX_ROUNDS:
        return force_finalize(conversation)   # truncated=true
    rounds += 1
    for tool_call in response.tool_calls:
        result = execute_tool(tool_call)
        conversation.append(tool_response)
```

项目里 [ToolCallingAgent.java](../../backend/src/main/java/dev/reboot/agent/ToolCallingAgent.java)
就是这套循环的通用落地：Day 78 先写在 `DeviceStatusAgentService`（`MAX_TOOL_ROUNDS=3`），
Day 79 提取为独立组件，签名 `run(systemPrompt, userPrompt, toolContext, toolCallbacks, maxRounds)`。

## 4. 为什么要硬限

模型可能反复请求工具，导致：

1. token 成本失控；
2. 请求超时；
3. 死循环。

硬限让 Agent 在预算内停止，并用一次“无工具收尾调用”给出结论。

## 5. 可观测性三件套

- `toolRounds`：执行了几轮工具；
- `toolCalls`：实际调用了几次工具；
- `toolTrace`：每次工具成功/失败；
- `truncated`：是否触达硬限；
- `referencedRealTime`：是否真的查了实时数据。

这些字段既给前端展示，也写入操作日志 `{ret}`，让 AI 行为可审计。

## 6. 工具的安全边界

工具代表“模型替用户访问数据”，因此必须与业务一致：

- 只读工具优先，不做副作用动作；
- 每个工具内部执行站点资源作用域（ADR 0020）；
- 工具失败返回 `{"error":"..."}`，让模型如实解释而不是中断整个 Agent。

---

## 7. Day 79：从专用循环到通用 `ToolCallingAgent`

Day 78 把 ReAct 循环写死在 `DeviceStatusAgentService`。Day 79 做了一次"泛化投资"：
把循环抽成独立 `@Component`，签名只依赖抽象的 `ToolCallback[]`，不绑定具体工具：

```java
public AgentRunResult run(String systemPrompt,
                          String userPrompt,
                          ToolContext toolContext,
                          ToolCallback[] toolCallbacks,
                          int maxRounds)
```

关键设计：

- **关闭自动循环**：`OpenAiChatOptions.internalToolExecutionEnabled(false)`（[L118](../../backend/src/main/java/dev/reboot/agent/ToolCallingAgent.java#L118)），
  让模型只"请求"工具，由代码决定执行——这是轮次可控的前提。
- **硬限 + 强制收尾**：`rounds >= maxRounds` 时不再执行工具，而是发一条
  "已达上限，请基于已有数据回答"的 `UserMessage`，让模型无工具收尾（`forceFinalize`），
  返回 `truncated=true`。不会硬中断留下半截对话。
- **未知工具降级**：模型请求了清单外的工具名时，回 `{"error":"未知工具: xxx"}`
  并记 trace，不抛异常、不中断整轮。
- **结果 record**：`AgentRunResult(answer, toolRounds, toolCalls, referencedRealTime,
  truncated, toolTrace)` 是不可变值对象，同时供前端展示与 `@OperationLog {ret}` 审计。

泛化的回报在 Day 83 兑现：巡检 Agent 复用同一个 `ToolCallingAgent`，只换 prompt、
换工具来源（MCP 适配器）、放宽硬限 3→6，循环逻辑一行没动。

### 7.1 多步分析场景的硬限放宽

ADR 0026 §2③ 规定：默认 3 轮，**多步推理场景显式放宽**。`DeviceAnalysisAgentService`
按 `get_device_basic → list_device_recent_data → alarms` 三步走，硬限放到 4 轮；
Day 83 巡检要跨设备逐台查询，放到 6 轮。硬限不是固定常数，而是"默认严、场景松"，
每次放宽都在 ADR + 代码注释里留痕，避免无声漂移。

## 8. Day 83：Agent + MCP 联调——让循环跨协议调工具

Day 82 已有 MCP 客户端，但只做"冒烟"。Day 83（ADR 0030）让 Agent 真正通过 MCP
工具完成巡检并生成日报。要解决的核心问题：MCP Server 暴露的是 `McpSchema.Tool`，
而 `ToolCallingAgent` 消费的是 Spring AI `ToolCallback`——两个生态需要适配器。

### 8.1 单 SSE 会话复用

一次巡检可能调 10+ 次工具。如果每次重连 SSE，握手成本与失败面都放大。
`McpClientService.openInspectionSession()` 建立连接 → `initialize()` 握手 →
`listTools()` 读清单，封装为 `McpInspectionSession`（`AutoCloseable`）。Agent 在会话内
复用同一个 `McpSyncClient`，结束 `close()` 统一释放；握手失败也会关闭已建客户端防泄漏。

### 8.2 `McpToolCallbackAdapter`：跨生态适配器

适配器把 MCP 工具转成 `ToolCallback`：

- `getToolDefinition()`：透传 MCP 工具名、描述、JSON Schema，模型零配置可调；
- `call()`：把模型生成的 JSON 参数转发到 `McpSyncClient.callTool`，文本结果原样返回；
- **错误同构**：MCP 工具返回 `isError` 或抛异常时，降级为 `{"error":"..."}`——这与
  内部 Agent 工具的失败语义完全一致（§6），所以 Agent 循环代码完全不用感知"工具来自
  哪个生态"。这是两个生态能拼在一起的真正原因。

### 8.3 复用而非重造循环

`McpInspectionAgentService` 不写循环，直接调 `toolCallingAgent.run(...)`，传入
`session.toolCallbacks()`（MCP 工具经适配器而来）与 6 轮硬限。会话同时统计去重设备数
与告警数，随 `AiInspectionReportResult` 返回。`truncated` 标记是否触达硬限，
prompt 要求超过 10 台时优先巡检离线/告警设备并注明未逐台覆盖。

### 8.4 权限收敛在 REST 入口

MCP 通道仍只读、无用户身份（ADR 0027/0029）。巡检权限不放进 MCP 工具上下文，
而是收敛在 `POST /api/ai/agents/inspection-report` 的 `@RequireRole(ADMIN)`：
只有 ADMIN 能触发全量巡检。MCP 1.1 OAuth 就绪后再用标准协议替换自定义令牌，
不手造身份头。

## 9. 关键文件

- [ToolCallingAgent.java](../../backend/src/main/java/dev/reboot/agent/ToolCallingAgent.java) — 通用 ReAct 循环
- [AgentRunResult.java](../../backend/src/main/java/dev/reboot/agent/AgentRunResult.java) — 运行结果 record
- [DeviceAnalysisAgentService.java](../../backend/src/main/java/dev/reboot/service/DeviceAnalysisAgentService.java) — Day 79 多步分析（4 轮）
- [McpInspectionAgentService.java](../../backend/src/main/java/dev/reboot/service/McpInspectionAgentService.java) — Day 83 巡检日报（6 轮）
- [McpInspectionSession.java](../../backend/src/main/java/dev/reboot/mcp/McpInspectionSession.java) — 单 SSE 会话 + 指标统计
- [McpToolCallbackAdapter.java](../../backend/src/main/java/dev/reboot/mcp/McpToolCallbackAdapter.java) — MCP→ToolCallback 适配器
- [ADR 0026](../decision-log/0026-agent-loop-governance.md) — Agent 循环治理
- [ADR 0030](../decision-log/0030-mcp-agent-inspection.md) — Agent + MCP 联调

---

> Week 12 收官。Agent 从"专用循环"泛化为"通用组件"，再跨协议接入 MCP 工具生态，
> 下一阶段（Week 13）走向"日报自动推送 + AI 与告警业务闭环"。
