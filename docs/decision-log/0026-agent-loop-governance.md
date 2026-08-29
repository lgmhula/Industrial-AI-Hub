# Decision 0026: Agent 循环边界与可观测性（Week 12 起步）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-29 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 78 / ADR 0023 / DeviceStatusAgentService |

## 1. 背景

Day 68 已在设备状态问答中实现了一个 ReAct 风格的工具调用循环：

```text
思考（模型决定调用工具） → 行动（执行工具） → 观察（结果回填） → 继续/回答
```

Week 12 要把“简单单目标 Agent”升级为“多步推理 Agent”（Day 79），并继续走向 MCP
（Day 80+）。在扩展前，需要先把 Agent 的循环边界与可观测性沉淀为明确契约，避免
后续 Agent 各自为政、token 成本失控或无法审计。

## 2. 决策

| 项 | 决策 |
|----|------|
| Agent 范式 | **ReAct（Reasoning + Acting）**，显式手动循环；不采用 Spring AI 自动工具循环 |
| 循环实现 | 参考 `DeviceStatusAgentService`：`while + 手动执行 + 结果回填对话`，每轮 `internalToolExecutionEnabled(false)` |
| 轮次硬限 | 默认最大 3 轮工具调用；达到硬限后追加收尾提示、无工具再调用一次，`truncated=true` |
| 调用计数 | 单轮内多个工具调用分别计数；结果携带 `toolRounds` / `toolCalls` / `toolTrace` |
| 工具约束 | 第一版工具只读（查询），禁止副作用；每个工具内部执行站点资源作用域（ADR 0020） |
| 未参考实时数据 | 零工具调用直接回答时 `referencedRealTime=false`，前端显式标注 |
| 审计 | `@OperationLog(operationType="FUNCTION_CALL", targetType="AI")`，`{ret}` 写入结果摘要 |
| 错误回退 | 工具 403/404 返回 `{"error":"..."}` 让模型如实解释，不中断整轮 |

### 2.1 为什么不用 Spring AI 自动循环

Spring AI 的 `internalToolExecutionEnabled(true)` 会递归执行工具直到模型不再请求，
无轮次上限，也无法精确统计轮次/调用数。手动循环把边界、计数、截断、回退全部变为
显式代码，符合工业场景的预算与审计要求。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| Spring AI 自动工具循环 | 无轮次上限，无法做 3 轮硬限与可观测性 |
| 让 Agent 直接返回自然语言而不调工具 | 失去“查询实时数据”能力，与 Function Calling 目标冲突 |
| 工具失败抛异常终止整轮 | 一个工具失败会中断整个问答，返回 `{"error"}` 更友好 |

## 4. 影响与验证

- 代码：Day 79 复用并泛化现有手动循环，提取可复用的 Agent 循环组件；
- 测试：轮次硬限、工具回填、未参考实时数据、站点作用域、审计字段；
- 文档：`docs/ai/agent-learning-notes.md`、AGENTS/DAILY_ROADMAP 同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| token 成本放大 | 3 轮硬限 + 单次请求超时 + 工具只读 |
| 模型臆造数据 | 系统提示词强制“先调工具再回答”；`referencedRealTime` 明示数据来源 |
| 工具返回 JSON 过大 | 列表 limit 上限与字段精简 |
| 多 Agent 各自实现导致漂移 | ADR 定契约，Day 79 抽公共循环组件 |

---

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
