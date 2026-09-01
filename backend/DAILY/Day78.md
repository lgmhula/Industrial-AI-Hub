# Day 78 — Agent 概念：ReAct 模式、工具调用循环（ADR 0026）

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 12（Agent + MCP）
> **分支**：`feat/agent-mcp`
> **配套 ADR**：[0026-agent-loop-governance.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0026-agent-loop-governance.md)
> **配套笔记**：[agent-learning-notes.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%82%AF%E5%AE%9A%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/ai/agent-learning-notes.md)
> **验收结果**：✅ **GO**（ADR 0026 决策闭环 + Agent 概念笔记成稿）

---

## 一、今日产出

| 项 | 路径 | 说明 |
| --- | --- | --- |
| ADR 0026 | `docs/decision-log/0026-agent-loop-governance.md` | ReAct 手动循环、3 轮硬限、工具只读、站点作用域、可观测性、审计 |
| Agent 笔记 | `docs/ai/agent-learning-notes.md` | ReAct 概念、循环伪代码、硬限与可观测性 |
| 状态同步 | AGENTS §3 / DAILY_ROADMAP Day 78 标记完成 | |

## 二、关键结论

1. Agent = ReAct 循环：思考 → 行动 → 观察 → 回答；
2. 不采用 Spring AI 自动循环，手动控制轮次与计数；
3. 可观测性 = `toolRounds/toolCalls/toolTrace/truncated/referencedRealTime`；
4. 工具只读 + 站点作用域，失败返回 `{"error"}` 不中断整轮。

## 三、明日计划（Day 79）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | 实现简单多步 Agent：先查设备 → 再查数据 → 再分析 |
| ★★☆ | 复用并泛化现有手动工具循环，抽公共 Agent 组件 |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day79 日志 |
