# Decision 0021: DeepSeek 作为 Phase 4 LLM 提供商（OpenAI 兼容）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-28 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 66 / DeepSeekProperties / DeepSeekClient / AiService / AiController / AGENTS.md §8 |

---

## 1. 背景

Phase 4 路线图原计划使用 OpenAI API。Day 66 开始时用户明确指定改用 DeepSeek API。
DeepSeek 提供 OpenAI 兼容的 Chat Completions 协议（/chat/completions），
默认模型 deepseek-chat（V3），并支持 response_format json_object 结构化输出与 usage token 统计。

## 2. 决策

| 项 | 决策 |
|----|------|
| LLM 提供商 | DeepSeek（默认 deepseek-chat，可切换 deepseek-reasoner） |
| 调用协议 | OpenAI 兼容 Chat Completions REST |
| HTTP 客户端 | Spring 6 RestClient（原生 HTTP，零新增 Maven 依赖） |
| 密钥 SSOT | dev：根目录 .env 的 DEEPSEEK_API_KEY（ADR 0015 机制）；test：application-test.yml 隔离占位；prod：启用时由 compose/容器环境注入 |
| 启用策略 | opt-in：deepseek.enabled 默认 false；未配置 Key 时 /api/ai/* 返回 503，核心业务不受影响 |
| 首个版本能力 | 非流式文本补全 + token 用量 + json_object 结构化输出（告警摘要 / 设备健康诊断） |
| 权限模型 | /api/ai/* 沿用 RBAC（VIEWER+），单对象场景继续走 SiteAccessService.assertSiteAccess |
| 错误语义 | 上游 4xx/5xx、未启用、缺 Key 统一 ErrorCode.SERVICE_UNAVAILABLE（503） |

### 2.1 关于空默认的说明

ADR 0015/AGENTS.md §8.3 要求基础设施密钥（MySQL/Redis/RabbitMQ/JWT）fail-fast。
DeepSeek 是可选外部付费服务，为不影响无 Key 环境的启动与现有部署，采用显式 opt-in：
DEEPSEEK_API_KEY 允许为空默认，但 deepseek.enabled=true 且 Key 缺失时请求期显式 503，
不允许静默返回伪结果或空字符串调用上游。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| com.openai:openai SDK 指向 DeepSeek baseUrl | 引入新依赖需版本治理与 ADR；当前仅需 Chat Completions 一个端点，RestClient 已够用 |
| Spring AI ChatClient 起步 | 重量级抽象，适合 Day 67+ 统一多模型/模板场景；Day 66 先打通协议与业务闭环 |
| 直接实现 Streaming/SSE | 需引入 WebFlux/Reactor 或手写 SSE 解析，Day 66 范围外；流式输出推迟到 Day 67-68 |

## 4. 影响与验证

- 接口：新增 POST /api/ai/chat、POST /api/ai/alarms/{id}/summary、POST /api/ai/devices/{id}/diagnose；
- 配置：deepseek.enabled/base-url/api-key/model/timeout-seconds/max-tokens/temperature，application.yml + .env.example 同步；
- 错误码：ErrorCode 新增 SERVICE_UNAVAILABLE(503)；
- 测试：DeepSeekClientTest（MockRestServiceServer 验证请求头/响应解析/上游错误）、AiServiceTest（提示词编排/JSON 解析/站点作用域）；
- 文档：路线图 Week 10 改为 DeepSeek；架构文档、AGENTS.md、SETUP.md、Day 66 日志同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| API 费用 / 限流 | 默认关闭；上游 429/5xx 映射 503 并记 ERROR 日志 |
| 生产误开但未注入 Key | enabled=true + Key 缺失时请求期显式 503（非静默） |
| 提示词注入 | 业务场景 system prompt 固定 + 只注入项目内设备/告警元数据，不执行模型返回的指令 |
| 敏感信息外泄 | 只发送设备基础信息/告警/最近数据摘要，不发送密码、token、用户隐私字段 |
