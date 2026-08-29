# Decision 0022: Spring AI ChatClient 抽象（DeepSeek OpenAI 兼容协议）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-28 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 67 / DeepSeekConfig / AiService / ADR 0021 / AGENTS.md §5 |

---

## 1. 背景

Day 66 使用手写 `DeepSeekClient`（Spring 6 RestClient）直接调 OpenAI 兼容的
`/chat/completions`，协议与业务闭环已打通。但该方案存在两个长期问题：

1. 提示词编排、参数装配散落在业务代码中，切换 OpenAI / Ollama / Zhipu 等提供商时
   需要改动 `AiService` 与协议 DTO；
2. 缺少统一的 Chat 抽象与模板能力，后续 RAG（PromptTemplate/Advisor）和
   Structured Output 需要重新引入抽象层。

ADR 0021 §3 已将「Spring AI ChatClient 起步」列为备选方案并明确推迟到 Day 67，
本次正式采纳。

## 2. 决策

| 项 | 决策 |
|----|------|
| 抽象层 | Spring AI ChatClient + PromptTemplate（`spring-ai-starter-model-openai`） |
| 版本治理 | Spring AI `1.0.3`，与 Spring Boot `3.5.0` 同代锁定；在 pom 显式声明版本，不经 BOM 隐式漂移 |
| 提供商接入 | OpenAI 兼容协议 Bean：`OpenAiApi`（baseUrl=DeepSeek）+ `OpenAiChatModel` + `ChatClient`，全部显式声明于 `DeepSeekConfig` |
| 配置 SSOT | 仍为 `DeepSeekProperties`（`deepseek.*`，dev 来自根目录 `.env`，ADR 0015）；不使用 `spring.ai.openai.*` 属性，避免双源漂移 |
| 业务改造范围 | `AiService.summarizeAlarm` / `diagnoseDevice` 改用 ChatClient + PromptTemplate；通用 `chat()` 保留 `DeepSeekClient` 协议层（token 用量 / 自定义模型 / 现有单测） |
| 结构化输出 | `OpenAiChatModel` 默认 options 固定 `response_format=json_object`，业务层沿用「文本 + Jackson 解析 + 失败降级纯文本」路径 |
| 启用策略 | 维持 opt-in：`deepseek.enabled=false` 或 Key 缺失时，`DeepSeekClient.ensureAvailable()` 统一 503，ChatClient 路径复用同一校验 |
| 自动配置 | Chat 自动配置由显式 Bean 优先接管（`@ConditionalOnMissingBean`）；Embedding/Image/Audio/Moderation 自动配置在 `application.yml` / `application-test.yml` 统一 exclude，避免未配置 `spring.ai.openai.api-key` 时启动失败 |

### 2.1 版本兼容依据

Spring AI `1.0.3` 的 Starter POM 声明 `spring-boot-starter:3.5.0`，与项目 parent
（Spring Boot 3.5.0）对齐；同时引入以下传递依赖，作为新增依赖面记录在案：

- `spring-ai-openai` / `spring-ai-model` / `spring-ai-client-chat`（核心抽象）
- `spring-ai-autoconfigure-model-openai` / `-chat-client` / `-chat-memory`
- Reactor/WebFlux（`OpenAiApi` streaming 基础设施，当前非流式调用不触发）
- Kotlin stdlib（Spring AI 客户端扩展）

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| 直接启用 `spring.ai.openai.*` 自动配置 | 密钥/模型参数将形成 `deepseek.*` 与 `spring.ai.openai.*` 双源，违反 ADR 0015 SSOT；DeepSeek 切换 OpenAI 时易产生隐藏配置漂移 |
| 全部调用替换为 ChatClient 并退役 DeepSeekClient | 通用 `chat()` 依赖 token 用量/自定义模型字段，保留协议层可继续复用 DeepSeekClientTest 与 503 语义；一次性删除会扩大回归面 |
| 引入 com.openai:openai SDK | 与 ADR 0021 结论一致：依赖面更大、无统一抽象收益，不采纳 |

## 4. 影响与验证

- 依赖：`backend/pom.xml` 新增 `spring-ai-starter-model-openai:1.0.3`；
- 配置：`DeepSeekConfig` 新增 3 个 Spring AI Bean，配置属性不变；
- 配置：`application.yml` / `application-test.yml` 排除 5 个未启用能力的 OpenAI 自动配置（Embedding/Image/AudioSpeech/AudioTranscription/Moderation）；
- 业务：`AiService` 提示词常量改为 PromptTemplate（`{deviceName}`、`{alarmType}` 等占位符），
  `callJson` 统一走 `chatClient.prompt().system(...).user(...).call().content()`；
- 测试：`AiServiceTest` 增加 ChatClient 链式 Mock 与空结果 503 用例；
  `DeepSeekConfigTest` 断言默认 options（model/temperature/maxTokens/JSON_OBJECT）；
  `DeepSeekClientTest` 不受影响（协议层保留）；
- 文档：路线图 Week 10 Day 67、架构文档、AGENTS §3、本 ADR、Day 67 日志同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| Spring AI 传递依赖体积增加（WebFlux/Reactor/Kotlin） | 记录于 §2.1；仅非流式调用，不启用 WebFlux 端点；如未来需瘦身可再评估裁剪 |
| ChatClient 默认 `json_object` 影响通用聊天 | 通用 `chat()` 不走 ChatClient（保留 DeepSeekClient 无 response_format 路径），结构化 JSON 仅业务场景生效 |
| Spring AI 版本升级漂移 | pom 显式锁定 1.0.3；后续升级必须走 ADR + 全量回归 |
| 提供商切换时行为差异 | ChatClient 抽象统一业务调用面；Provider 特有参数收敛在 DeepSeekConfig 一处 |

---

> 最后更新：2026-08-28 | 维护者：AI 助手 + hula0710
