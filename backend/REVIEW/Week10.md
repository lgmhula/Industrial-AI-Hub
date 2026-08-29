# Week 10 复盘 — Phase 4 AI 集成第一周（DeepSeek → Spring AI → Function Calling）

> 日期：2026-08-28 ~ 2026-08-29 | 覆盖：Day 66 ~ Day 70

---

## 一、本周目标 vs 实际

| 目标 | 实际 | 状态 |
|------|------|:----:|
| Day 66: DeepSeek API 基础 + 告警摘要/设备诊断 | 手写 RestClient 打通 OpenAI 兼容协议，3 个 AI 端点 + json_object 结构化输出，ADR 0021 | ✅ |
| Day 67: Spring AI ChatClient/PromptTemplate 抽象 | 引入 `spring-ai-starter-model-openai:1.0.3`，业务层 ChatClient 化，前端两个 AI 入口 + Flyway V9 AI 操作日志，ADR 0022 / TD-028 | ✅ |
| Day 68: Function Calling | `@Tool` 声明式三工具 + 手动 Agent 循环 + 3 轮硬限 + 设备问答面板，Flyway V10，ADR 0023 | ✅ |
| Day 69: 前端工业化视觉升级 | DESIGN.md 深色控制中心 + Element Plus 暗色覆盖 + 表格/AI 卡/认证页统一 + ECharts 按需注册 | ✅ |
| Day 70: 周复盘 + AI 模块笔记 | 本复盘 + `docs/ai/phase4-ai-learning-notes.md`，分支整理准备 PR | ✅ |

## 二、关键收获

### 2.1 DeepSeek 是 OpenAI 兼容协议的“平替”

Day 66 没有引入任何 OpenAI SDK，直接用 Spring 6 `RestClient` 调
`/chat/completions` 就完成了 `deepseek-chat` 接入。真正的成本不在协议，而在：

- 协议 DTO 与 usage/token 统计的映射；
- `response_format=json_object` 后的 JSON 解析与纯文本降级；
- opt-in 启用策略与 503 fail-fast，避免 AI 服务拖垮核心业务。

**启示**：兼容协议降低了切换成本，但配置单一事实源（SSOT）和错误语义仍要自己守住。

### 2.2 Spring AI 的价值在“抽象面”而不是“少写代码”

Day 67 引入 Spring AI 后，`summarizeAlarm` / `diagnoseDevice` 从手写提示词拼接
变成 `PromptTemplate + ChatClient`。但它不是全量替换：通用 `chat()` 继续保留
`DeepSeekClient`，因为 token 用量和自定义模型字段在抽象层拿不到等价能力。

**启示**：抽象层应服务业务面，而不是为了“统一”而牺牲必要能力。双入口策略是有意为之。

### 2.3 Function Calling 的关键不是“能调工具”，而是“可控地调工具”

Day 68 选择关闭 Spring AI 自动循环，自己实现 `while + 3 轮硬限`。原因是自动循环
没有轮次上限，也无法统计调用数和判断“是否参考实时数据”。手动循环把审计、回退、
截断都变成了显式代码。

**启示**：Agent 能力的边界必须由业务约束，而不是框架默认行为。

### 2.4 AI 端点也是敏感操作

Day 67 给三个 AI 端点补 `@OperationLog`，Day 68 用 `{ret}` 占位符把
`deviceId/rounds/calls/referencedRealTime` 写进审计日志。AI 调用既是外部付费
调用，也是 RBAC 敏感操作，审计闭环和业务功能同样重要。

## 三、Phase 4 第一周演进全景

```
Day 66  DeepSeekClient (RestClient)  ── 协议打通 + token 统计 + 结构化 JSON
            │
Day 67  ChatClient + PromptTemplate  ── 业务层抽象 + 前端入口 + AI 审计 (V9)
            │
Day 68  @Tool + 手动 Agent 循环      ── 实时数据工具调用 + 3 轮硬限 (V10)
            │
Day 69  DESIGN.md 视觉升级           ── 深色工业控制中心 + 表格/AI 卡统一
```

三段式演进形成了清晰的层次：

1. **协议层**：DeepSeekClient 负责 HTTP、token、503；
2. **抽象层**：ChatClient/PromptTemplate 负责业务提示词编排；
3. **能力层**：@Tool + Agent 负责让模型主动查询真实数据。

## 四、第四阶段第一周检查点

> 第一周目标：DeepSeek API → RAG → Agent → MCP 中，先完成“让 AI 服务业务”的最小闭环。

| 能力 | 证据 |
|------|------|
| 文本补全 | `POST /api/ai/chat`（token 统计 + 自定义模型） |
| 告警摘要 | `POST /api/ai/alarms/{id}/summary`（结构化 JSON + 站点作用域） |
| 设备诊断 | `POST /api/ai/devices/{id}/diagnose`（自动注入最近数据/告警） |
| 设备问答 Agent | `POST /api/ai/agents/device-status`（3 工具 + 3 轮硬限） |
| AI 审计 | Flyway V9/V10 + `@OperationLog` + `{ret}` 摘要 |
| 前端入口 | 告警 AI 摘要 Dialog + 设备诊断卡 + 设备问答折叠面板 |

**检查点结论**：AI 已从“被动补全”走到“主动查数再回答”，业务闭环成立。

## 五、技术债务状态

| ID | 说明 | 状态 |
|----|------|------|
| TD-028 | AI 端点无操作日志 | ✅ Day 67 解决 |
| TD-031 | 前端 chunk > 500kB | ✅ Day 69 解决（按需注册 ECharts） |
| TD-032 | 失败场景 `{ret}` 输出 "null" | ✅ Day 68 修复 |
| TD-033 | AI 可用性检查掩盖 404/403 | ✅ Day 68 修复 |
| TD-029/030 | Spring AI 传递依赖体积 / V9 MySQL IT 缺口 | ⏳ 遗留观察（P2/P3） |

## 六、不足与改进

1. **未配置真实 DeepSeek Key 跑端到端**：当前验收覆盖 503 语义与单测，真实模型输出、token 计费、工具回填仍需 Key 后预演；
2. **AI 工具目前只读**：3 个工具都是查询，尚未覆盖“确认告警/下发控制”等有副作用动作，风险可控但能力上限清晰；
3. **对话历史未持久化**：设备问答面板每次刷新即清空，后续可做会话上下文或服务端历史；
4. **RAG/MCP 尚未开始**：Week 11 是下一阶段重点。

## 七、下周展望（Week 11：RAG + 知识库）

| 天 | 任务 |
|----|------|
| Day 71 | RAG 概念 + 向量库选型 |
| Day 72 | 文档切片 + embedding + 入库 |
| Day 73 | 检索实现 |
| Day 74-75 | 设备手册知识库 + AI 运维助手实战 |
| Day 76 | 前端 AI 助手对话页 |
| Day 77 | 周复盘 + RAG 笔记 |

> Phase 4 第一周收官。下一阶段从“实时数据工具”走向“私有知识库检索”。
