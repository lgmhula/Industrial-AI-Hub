# Week 12 Agent 学习笔记：ReAct 与工具调用循环

> 日期：2026-08-29 | 覆盖：Day 78（ADR 0026）

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

项目里 [DeviceStatusAgentService.java](../../backend/src/main/java/dev/reboot/service/DeviceStatusAgentService.java)
就是这套循环的落地：`MAX_TOOL_ROUNDS=3`，手动执行工具并回填。

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

> 下一步：Day 79 提取可复用的多步推理 Agent 组件。
