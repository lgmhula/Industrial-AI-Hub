# Day 66 — Phase 4 AI 集成起步：DeepSeek API 基础 + 告警摘要 / 设备诊断

> **日期**：2026-08-28
> **阶段**：Phase 4 AI 集成启动（路线图原计划 OpenAI → 改用 DeepSeek）
> **分支**：`main`（codex 直接介入本分支；已由 hula0710 + 验证 Agent 验收）
> **配套 ADR**：[0021-deepseek-llm-provider.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0021-deepseek-llm-provider.md)
> **验收结果**：✅ **GO**（验证 Agent 执行 191 tests 全绿 + AI 冒烟 7/7 + 前端 build 0 error）

> **文档补录说明**：codex 执行本任务时未写入 Day 日志文件，本文件由验证 Agent 在验收阶段按实际交付内容补录（以满足 AGENTS §4.3 文档同步要求）。

***

## 一、交付范围（对照 ADR 0021 §2 全部达成）

| # | ADR 0021 决策项                                                | 交付结果   | 证据                                                                                                   |
| - | ----------------------------------------------------------- | ------ | ---------------------------------------------------------------------------------------------------- |
| 1 | LLM 提供商 = DeepSeek（OpenAI 兼容协议）                             | ✅ 已切换  | `DeepSeekClient` 调 `/chat/completions`，base-url 默认 `https://api.deepseek.com`                        |
| 2 | HTTP 客户端 = Spring 6 RestClient（零新增 Maven 依赖）                | ✅ 零新增  | pom.xml 无 `spring-ai/openai/webflux/reactor` 引入（Grep 验证）                                             |
| 3 | 密钥 SSOT = 根目录 `.env` `DEEPSEEK_API_KEY`                     | ✅ 符合   | `application.yml` `${DEEPSEEK_API_KEY:}` + `.env.example` L46-L53 同步；DeepSeekConfig Bean 注入          |
| 4 | opt-in 启用：enabled 默认 false；缺 Key / 未启用时 `/api/ai/*` 统一 503  | ✅ 生效   | 冒烟 7/7：`DeepSeek AI 服务未启用，请配置 DEEPSEEK_ENABLED=true` 503 语义正确                                        |
| 5 | 首个版本能力 = 非流式补全 + token 用量 + json\_object 结构化输出              | ✅ 3 接口 | chat（通用） + alarms/{id}/summary + devices/{id}/diagnose                                               |
| 6 | 权限模型 = `/api/ai/*` VIEWER+，单对象走 SiteAccessService           | ✅ 生效   | `@RequireRole({VIEWER,OPERATOR,ADMIN})`；AiService summarizeAlarm/diagnoseDevice 内 `assertSiteAccess` |
| 7 | 错误语义 = 上游 4xx/5xx/空结果/未启用/缺 Key → SERVICE\_UNAVAILABLE(503) | ✅ 一致   | `ErrorCode.SERVICE_UNAVAILABLE(503, "第三方服务暂不可用")`；GlobalExceptionHandler 映射 → HTTP 503               |

***

## 二、新增/修改文件清单（共 22 个文件）

### 2.1 Java 源码（13 个 = 主代码）

| 分层         | 文件                                                                                                                                                                                                                                     | 职责                                                                                                          |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Controller | [AiController.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/controller/AiController.java)         | 3 REST 端点；构造器注入 AiService；返回 `ApiResponse<T>` 包裹                                                            |
| Service    | [AiService.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/service/AiService.java)                  | chat / summarizeAlarm / diagnoseDevice 三方法；提示词编排；JSON 解析失败降级；SiteAccess 站点作用域                               |
| Client     | [DeepSeekClient.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/client/DeepSeekClient.java)         | RestClient POST `/chat/completions`；ensureAvailable（未启用/缺 Key 503）；上游错误 → Error 日志 + 503                    |
| Config     | [DeepSeekProperties.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/config/DeepSeekProperties.java) | 7 属性：enabled/baseUrl/apiKey/model/timeoutSeconds/maxTokens/temperature（默认 deepseek-chat / 30s / 1024 / 0.3） |
| Config     | [DeepSeekConfig.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/config/DeepSeekConfig.java)         | `@EnableConfigurationProperties`；`deepSeekRestClient` Bean（Base URL / Content-Type / Bearer 授权 / 30s 超时）    |
| DTO(协议)    | `DeepSeekChatRequest.java` / `DeepSeekChatResponse.java` / `DeepSeekChoice.java` / `DeepSeekMessage.java` / `DeepSeekUsage.java` / `DeepSeekResponseFormat.java`                                                                       | 6 个：与 OpenAI Chat Completions 协议完全对齐（`json_object` response\_format 支持）                                     |
| DTO(业务)    | `AiChatRequest.java`                                                                                                                                                                                                                   | message(NotBlank+4000) / systemPrompt(2000) / model(64)；**@Valid** 生效（冒烟：空 message → 400 ✅）                 |
| DTO(业务)    | `AiChatResult.java`                                                                                                                                                                                                                    | content + model + promptTokens / completionTokens / totalTokens + finishReason                              |
| DTO(业务)    | `AiAlarmSummary.java`                                                                                                                                                                                                                  | summary + possibleCauses\[] + suggestedActions\[] + priority（高/中/低）                                         |
| DTO(业务)    | `AiDeviceDiagnosis.java`                                                                                                                                                                                                               | healthLevel（健康/关注/异常） + summary + issues\[] + suggestedActions\[]                                           |

### 2.2 Java 测试（2 个 = 11 tests）

| 文件                                                                                                                                                                                                                                     | 测试数 | 覆盖要点                                                                                                                                                                                               | 验证结果                                                         |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| [DeepSeekClientTest.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/test/java/dev/reboot/client/DeepSeekClientTest.java) | 4   | ① 请求头/响应/usage 解析 ② disabled 抛 503 ③ 缺 Key 抛 503 ④ 上游 401 抛 503                                                                                                                                    | ✅ 4/4（MockRestServiceServer 验证 Bearer 头 + JSON Content-Type） |
| [AiServiceTest.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/test/java/dev/reboot/service/AiServiceTest.java)          | 7   | ① chat 提示词编排 + token 统计 ② 空响应 503 ③ summarizeAlarm 结构化 JSON + siteAccess.assertSiteAccess ④ JSON \`\`\` fence unwrap ⑤ 纯文本 fallback（WARN 日志降级） ⑥ diagnoseDevice 最近数据/告警注入 ⑦ NOT\_FOUND 告警/设备 抛 404 | ✅ 7/7（WARN 「JSON 解析失败，退回纯文本」是预期 fallback 路径，非错误）             |

### 2.3 配置 + 文档（3 个 = 3 处修改 + 1 ADR + 1 本日志）

| 项               | 路径                                                                                                                                                                                                                           | 修改说明                                                                                                                    |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| application.yml | L84-L96                                                                                                                                                                                                                      | `deepseek` 段 7 属性：`enabled:${DEEPSEEK_ENABLED:false}` base-url api-key model timeout-seconds max-tokens temperature=0.3 |
| .env.example    | L46-L53                                                                                                                                                                                                                      | `DEEPSEEK_ENABLED=false` + 其余 5 变量（API\_KEY 占位 change\_me，BASE\_URL 默认官方）                                               |
| ErrorCode.java  | L41                                                                                                                                                                                                                          | 新增 `SERVICE_UNAVAILABLE(503, "第三方服务暂不可用")`                                                                              |
| ADR 0021        | [0021-deepseek-llm-provider.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0021-deepseek-llm-provider.md) | 决策背景 + 3 备选方案（Spring AI / OpenAI SDK / Streaming）+ 风险缓解，**全部与实际实现一致** ✅                                                 |

***

## 三、3 个 AI REST 接口契约

### 3.1 通用文本补全

```
POST /api/ai/chat
Headers: Authorization: Bearer <JWT> （权限：VIEWER+）
Body:
  {
    "message": "你好，帮我总结设备情况",           // @NotBlank, max 4000
    "systemPrompt": "你是工业助手，输出简洁中文",   // 可选 max 2000
    "model": "deepseek-chat"                       // 可选 max 64；默认配置值
  }
Response 200 ApiResponse<AiChatResult>:
  { "code": 200, "message": "success",
    "data": {
      "content": "...", "model": "deepseek-chat", "finishReason": "stop",
      "promptTokens": 18, "completionTokens": 50, "totalTokens": 68
    } }
Error 400 参数错 / 401 未登录 / 403 越权 / 503 未启用或缺 Key 或上游错误
```

### 3.2 告警摘要（结构化 JSON + 站点权限）

```
POST /api/ai/alarms/{alarmId}/summary   （@RequireRole VIEWER+）
SiteAccessService.assertSiteAccess(userId, device.siteId, VIEWER)
Response 200 ApiResponse<AiAlarmSummary>:
  { "summary": "...", "possibleCauses": ["探头老化","线路接触不良"],
    "suggestedActions": ["现场检查传感器接线","重新校准"], "priority": "高" }
Error 404 告警不存在 / 403 越权访问其他站点告警 / 503 DeepSeek 问题
```

### 3.3 设备健康诊断（结构化 JSON + 站点权限）

```
POST /api/ai/devices/{deviceId}/diagnose   （@RequireRole VIEWER+）
SiteAccessService.assertSiteAccess(userId, device.siteId, VIEWER)
输入上下文（AiService 自动注入）：
  - 设备基础信息（名称/编码/类型/状态/位置）
  - 最近 10 条 device_data
  - 最近 5 条 alarm（含等级/状态）
Response 200 ApiResponse<AiDeviceDiagnosis>:
  { "healthLevel": "关注", "summary": "...",
    "issues": ["运行温度超过阈值 15%","最近 3 条高优先级告警"],
    "suggestedActions": ["降低负载","现场热成像检查","更换冷却风扇"] }
```

***

## 四、验收结果（由验证 Agent 执行）

### 4.1 后端 Tests

```
./mvnw test → BUILD SUCCESS
Tests run: 191, Failures: 0, Errors: 0, Skipped: 0
 ├─ AiServiceTest: 7/7 ✅
 └─ DeepSeekClientTest: 4/4 ✅
```

### 4.2 AI API 运行冒烟（7/7 ✅，默认 disabled=503 场景）

| # | 场景                                                 | 预期               | 实际                                                  |
| - | -------------------------------------------------- | ---------------- | --------------------------------------------------- |
| 1 | 匿名 `/api/ai/chat`                                  | 401 未授权          | 401 ✅                                               |
| 2 | VIEWER `/api/ai/chat`                              | 503 DeepSeek 未启用 | 503 ✅                                               |
| 3 | VIEWER `/api/ai/alarms/1/summary`（alarmId=1 存在）    | 503              | 503 ✅                                               |
| 4 | VIEWER `/api/ai/devices/1/diagnose`（deviceId=1 存在） | 503              | 503 ✅                                               |
| 5 | ADMIN `/api/ai/chat` 空 message（@Valid @NotBlank）   | 400 参数校验         | 400 ✅                                               |
| 6 | ADMIN `/api/ai/chat` 正常请求（同 VIEWER）                | 503              | 503 ✅                                               |
| 7 | 503 message 语义（含「未启用/未配置/第三方」）                     | YES              | YES ✅「DeepSeek AI 服务未启用，请配置 DEEPSEEK\_ENABLED=true」 |

### 4.3 前端

```
npm run build → ✓ built in 723ms（0 error，仅 chunk size 警告）
路由无 AI 页面（符合 Day66 仅后端 API 范围；前端接 Day67 ChatClient 后接入）
```

### 4.4 数据存在性验证（避免 404 伪装 503）

* `GET /api/alarms?page=1&size=1` → **200**（告警表有数据）

* `GET /api/devices/1` → **200**（设备 1 存在，siteId=1 可过站点权限）

### 4.5 未采纳 / 遗留未实现项（对齐 ADR 0021 §3）

| 能力                           | 状态                               | 计划日                                      |
| ---------------------------- | -------------------------------- | ---------------------------------------- |
| 流式输出（SSE / streaming）        | ❌ Day66 范围外                      | Day 67-68                                |
| Spring AI ChatClient 抽象      | ❌ 未做                             | **Day 67（AGENTS §3 下一步已标注）**             |
| deepseek-reasoner（R1 推理模型）切换 | ⚠️ DTO 未覆盖 reasoning\_content 字段 | 接入时补协议 DTO                               |
| 前端 AI 页面接入                   | ❌ 未做（路由/GUI 无）                   | Day 67+（Dashboard 告警摘要 btn + 设备详情诊断 btn） |
| MCP / RAG / Agent 框架         | ❌ 路线图后续                          | 视 Phase 4 进度推进                           |

***

## 五、小缺陷 / 技术债务识别（非阻塞 Day66 验收，建议治理周处理）

| ID     | 项                                                                                                           | 风险级别 | 说明                                                                                                                                             |
| ------ | ----------------------------------------------------------------------------------------------------------- | ---- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| TD-024 | `DeepSeekProperties.apiKey` 默认空字符串 + `application.yml` `${DEEPSEEK_API_KEY:}`（AGENTS §8.3 严格模式下「敏感变量禁止空默认」） | 中    | 但 ADR 0021 §2.1 已明确 DeepSeek 为 **可选付费服务 opt-in 例外**，与 JWT/MySQL 等核心基础设施区分。补：若 `enabled=true` + Key 为空，启动期 warn 日志提醒；当前已在请求期 fail-fast 503，功能安全 |
| TD-025 | `AiAlarmSummary.priority`、`AiDeviceDiagnosis.healthLevel` 无枚举约束（纯 String）                                   | 低    | 今日 2 业务 DTO 都依赖模型输出；如做前端枚举颜色映射建议补 Enum                                                                                                         |
| TD-026 | DTO 类缺少 `@Schema` 注解（Knife4j 文档可读性）                                                                         | 低    | 与其他模块 DTO 风格一致；可选补                                                                                                                             |
| TD-027 | `SiteAccessService` 单测未覆盖 AiService 中的 assertSiteAccess 调用                                                  | 低    | 但 AiServiceTest 已 `verify(siteAccessService).assertSiteAccess(...)` → 行为已被验证                                                                   |
| TD-028 | AI 端点无操作日志（`@OperationLog` 未加）                                                                              | 中    | RBAC 敏感操作 + 计费外部调用；建议 `AiController` 三方法加 `@OperationLog(targetType="AI", operationType="CHAT/SUMMARY/DIAGNOSE")`，需扩展 chk\_target\_type 枚举     |

> **注意**：AGENTS §8.3 严格禁止的「JWT/REDIS/MYSQL/RABBITMQ 空默认」与 DeepSeek 不同；ADR 0021 已对 DeepSeek 明确 opt-in。若未来需要更严，可改 `enabled=true 启动期检查 Key`。

***

## 六、明日计划（Day 67）—— AGENTS §3 已标注同步

1. **Spring AI ChatClient 引入**（替换手写 `RestClient` 的部分或抽象包装层）：

   * 在 pom 显式引入 `spring-ai-openai-spring-boot-starter`（**需先写 ADR 0022，版本对齐 Spring AI 与 Spring Boot 3.5**）

   * 好处：统一 ChatClient 接口，未来切换 OpenAI / Zhipu / Ollama 零业务代码改动；内置 PromptTemplate / StructuredOutput
2. **前端接入 AI 能力**（两个优先入口，小而美）：

   * 告警列表行新增「🤖 AI 摘要」按钮 → 调 `/api/ai/alarms/{id}/summary` → 弹窗展示 summary/priority/actions

   * 设备详情页新增「🤖 AI 健康诊断」卡片 → 调 `/api/ai/devices/{id}/diagnose`

   * （可选）独立 **AI 聊天实验室**页面：/ai-chat 路由，给运营/管理员做文本补全 playground
3. **治理小债务**：

   * TD-028 给 `AiController` 三方法加 `@OperationLog`（扩展 chk\_target\_type：新增 'AI' 枚举值 → Flyway V9 迁移）

   * TD-024 启动期 `enabled=true & apiKey.isBlank()` 加 warn 日志

***

> **验证 Agent**：TRA验证助手 + hula0710
> **执行 Day 66 codex**：Codex（Phase 4 介入）
> **验收结论文档**：本日志 + ADR 0021 + AGENTS §3 状态
> **Release Gate**：✅ **GO（Day 66）** → 下一步 Day 67 Spring AI ChatClient 抽象 + 前端接入

