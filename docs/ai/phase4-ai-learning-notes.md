# Phase 4 AI 模块学习笔记：DeepSeek API → Spring AI ChatClient → Function Calling

> 日期：2026-08-29 | 覆盖：Day 66 ~ Day 68（对应 ADR 0021 / 0022 / 0023）

---

## 0. 学习主线

Phase 4 不是“做聊天机器人”，而是让 AI 服务工业设备管理业务。第一周完成了三段演进：

```
① 协议打通  → ② 业务抽象  → ③ 主动查数
DeepSeek     Spring AI      Function Calling
RestClient   ChatClient     @Tool + Agent 循环
```

每一段解决一个明确问题，而不是堆砌“更高级的框架”。

---

## 1. DeepSeek API：OpenAI 兼容协议的最小实现

### 1.1 核心认知

DeepSeek 的 `/chat/completions` 与 OpenAI Chat Completions 协议兼容，
所以不需要专用 SDK，用 Spring 6 `RestClient` 即可。关键字段：

```text
POST /chat/completions
Authorization: Bearer <DEEPSEEK_API_KEY>
{
  "model": "deepseek-chat",
  "messages": [{"role":"system","content":"..."},{"role":"user","content":"..."}],
  "temperature": 0.3,
  "max_tokens": 1024,
  "response_format": {"type": "json_object"}
}
```

响应中需要保留两个信息：

- `choices[0].message.content`：模型输出文本；
- `usage.prompt_tokens/completion_tokens/total_tokens`：用量统计，后续可做成本核算。

### 1.2 结构化输出与降级

`response_format=json_object` 让模型尽量返回 JSON，但模型仍可能输出
Markdown 代码围栏或纯文本。因此业务层必须有兜底：

```java
// 1) 去围栏 2) Jackson 解析 3) 失败降级纯文本
```

这是 LLM 工程的常态：模型是概率输出，边界必须由代码守住。

### 1.3 opt-in 与 503

DeepSeek 是可选付费服务，默认 `deepseek.enabled=false`。未启用或缺 Key 时，
所有 `/api/ai/*` 统一返回 503，而不是让核心业务受拖累或静默返回伪结果。

---

## 2. Spring AI ChatClient：业务层的统一抽象

### 2.1 为什么抽象

Day 66 手写协议的问题：提示词拼接、参数装配散落在业务代码。切换
OpenAI / Ollama / Zhipu 时需要改 `AiService` 和协议 DTO。

Spring AI 提供：

- `ChatClient`：链式 prompt/system/user/call/content；
- `PromptTemplate`：`{deviceName}` 等占位符渲染；
- 统一 Chat 抽象，未来 RAG 的 Advisor / Structured Output 可复用。

### 2.2 版本治理与 SSOT

- 版本显式锁定 `1.0.3`，与 Spring Boot `3.5.0` 同代，不依赖 BOM 隐式漂移；
- 配置仍走 `DeepSeekProperties`（`deepseek.*`），**不使用** `spring.ai.openai.*`，
  避免双源漂移（延续 ADR 0015 密钥 SSOT）。

### 2.3 双入口策略

```java
chat()              → DeepSeekClient  // token 用量 / 自定义模型
summarizeAlarm()    → ChatClient + PromptTemplate
diagnoseDevice()    → ChatClient + PromptTemplate
```

抽象层服务“业务结构化输出”，协议层保留“需要精细控制”的通用补全。这不是没重构干净，
而是有意保留能力边界。

---

## 3. Function Calling：让 AI 主动查询实时数据

### 3.1 ReAct 思想

前两段是“服务端把数据拼进提示词 → 单次补全”。Function Calling 改为：

```text
用户提问 → 模型决定调哪些工具 → 执行工具 → 把结果回填 → 模型生成最终答案
```

模型不再只能被动回答，而是能“推理 + 行动”（ReAct）。

### 3.2 @Tool 声明式注册

Spring AI 用注解声明工具，零手写 JSON Schema：

```java
@Tool(name = "get_device_basic", description = "查询设备基础信息")
public String getDeviceBasic(@ToolParam(description = "设备 ID") Long deviceId,
                             ToolContext toolContext) { ... }
```

`ToolCallbacks.from(bean)` 一行生成 `ToolCallback[]`。

### 3.3 手动循环 + 3 轮硬限

Spring AI 自动循环没有轮次上限。本项目显式关闭
`internalToolExecutionEnabled(false)`，自己实现循环：

```text
while (rounds < MAX_TOOL_ROUNDS) {
    response = chatModel.call(prompt with tools)
    if (no tool calls) return final answer
    for (toolCall) {
        result = callback.call(toolCall.arguments, toolContext)
        conversation.add(toolResponse)
    }
    rounds++
}
// 达到硬限 → 不带工具再补一轮收尾，truncated=true
```

这样轮次、调用数、是否参考实时数据都变成可统计、可审计、可回退的显式状态。

### 3.4 站点资源作用域

工具就是“模型替用户访问数据”，所以必须与业务一致走 ADR 0020：

```text
userId 经 ToolContext 传入 → SiteAccessService.assertSiteAccess(VIEWER)
```

工具失败不抛异常终止整轮，而是返回 `{"error":"..."}` 让模型如实解释。

---

## 4. 核心概念速查

| 概念 | 一句话理解 |
|------|-----------|
| Chat Completions | 给定消息列表，模型补全下一条消息 |
| Token | 文本切分单位，计费与上下文窗口都按 token |
| PromptTemplate | 占位符模板，运行时渲染系统/用户提示词 |
| Structured Output | 要求模型按 JSON 等格式输出，配合解析降级 |
| Tool/Function Calling | 模型输出“要调用哪个工具+参数”，由代码执行后回填 |
| ReAct | 推理与行动交替的 Agent 模式 |
| Hard Limit | 业务层对工具调用轮次的上限，防止死循环与 token 放大 |
| SSOT | 单一事实源，配置/密钥不允许多处漂移 |

---

## 5. 工程约束回顾

- 未启用 AI 时 fail-fast 503，不阻塞核心业务；
- 所有 AI 端点 RBAC（VIEWER+）+ 单对象站点作用域；
- 所有 AI 端点 `@OperationLog`，外部计费调用可审计；
- 模型输出永远有解析降级，不信任“一定返回 JSON”；
- 工具只读、轮次硬限、上下文精简约简，控制 token 成本。

> 下一阶段：RAG（检索增强生成），把私有设备手册/运维知识变成可检索上下文。
