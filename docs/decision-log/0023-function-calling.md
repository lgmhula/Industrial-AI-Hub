# Decision 0023: Spring AI @Tool 声明式 Function Calling（设备状态问答 Agent）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-29 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 68 / DeviceStatusAgentService / DeviceAiTools / AiController / ADR 0020 / ADR 0021 / ADR 0022 |

---

## 1. 背景

Day 66-67 的 AI 能力（告警摘要 / 设备诊断）是「服务端把数据拼进提示词 → 单次 LLM 补全」，
模型无法自主决定查什么数据，所有上下文都需要业务代码预取。Week 10 排期要求
「Function Calling：AI 自动调用项目接口查询设备状态」，即让模型在对话中自主决定
调用哪些查询工具（ReAct 风格），并约束调用行为可审计、可回退。

Spring AI 1.0.3（ADR 0022 已引入）提供 `@Tool` 注解声明式工具注册，与既有
ChatClient/PromptTemplate 抽象同栈，无需再引入 OpenAI 原生 tools 协议 DTO。

## 2. 决策

| 项 | 决策 |
|----|------|
| 工具协议 | Spring AI `@Tool` / `@ToolParam` 注解声明（零手写 JSON Schema），`ToolCallbacks.from(bean)` 生成 `ToolCallback[]` |
| 工具集（最小 3 个） | `get_device_basic`（设备基础信息）/ `list_device_recent_alarms`（单设备最近告警）/ `list_active_alarms_by_site`（站点未处理告警），集中于 `DeviceAiTools`（`dev.reboot.tool`） |
| 新接口 | `POST /api/ai/agents/device-status`（`AiDeviceStatusRequest{deviceId, question}` → `AiDeviceStatusResult{answer, toolRounds, toolCalls, referencedRealTime, truncated, toolTrace}`），前端设备详情页问答折叠面板 |
| Agent 循环 | `DeviceStatusAgentService`：系统提示词 + 设备上下文预置 → 循环「模型请求工具 → 手动执行 → 结果回填对话」；每轮 `OpenAiChatOptions.internalToolExecutionEnabled(false)` 关闭 Spring AI 自动循环（见 §5 风险） |
| 轮次硬限 | 最大 **3 轮**工具调用；达到后不再执行新工具，追加收尾提示无工具调用一次，`truncated=true` |
| 未参考实时数据 | 模型未调用任何工具直接回答 → `referencedRealTime=false`，前端标注「未参考实时数据」 |
| 站点作用域 | 当前用户 ID 经 `ToolContext` 传入工具，工具内 `SiteAccessService.assertSiteAccess(VIEWER)`（ADR 0020）；403/404 返回 `{"error": "..."}` JSON 让模型如实解释，而非整轮失败 |
| 审计 | Flyway V10 扩展 `chk_operation_type` 允许 `FUNCTION_CALL`；端点 `@OperationLog(operationType="FUNCTION_CALL", targetType="AI")`，`description` 新增 `{ret}` 占位符由 `OperationLogAspect` 替换为结果摘要（设备 ID / 轮次 / 调用数 / 是否参考实时数据） |

### 2.1 工具调用轮次定义

> 1 轮 = 模型返回一轮 `tool_calls` 并执行之。3 轮硬限下最多执行 3 批工具调用；
> 第 4 次模型仍请求工具时触发硬限，转向收尾（总 LLM 调用数 = 已执行轮次 + 收尾 1 次）。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| ChatClient `.tools(...)` 自动循环（`internalToolExecutionEnabled=true`） | `OpenAiChatModel.internalCall` 的自动循环**无轮次上限**（递归直至模型不再请求工具），无法满足「最大 3 轮硬限」，且无法统计调用数/判定「未参考实时数据」 |
| 自研 OpenAI function_call 协议 DTO + 手写 JSON Schema | 与 ADR 0022 抽象层目标相悖，重复造轮子；`@Tool` 零 Schema 声明即可获得工具定义 |
| 工具直接注入 Mapper 不做站点校验 | 模型调用工具即代表用户访问数据，必须与业务模块一致执行 ADR 0020 站点资源作用域 |
| 工具 403/404 抛异常终止整轮 | 一个工具失败会中断整个 Agent 对话；返回 `{"error"}` JSON 让模型能向用户如实说明（如「您无权访问该设备」） |

## 4. 影响与验证

- 依赖：无新增（复用 `spring-ai-starter-model-openai:1.0.3`）；
- 代码：新增 `tool/DeviceAiTools`、`service/DeviceStatusAgentService`、`dto/ai/AiDeviceStatus*`、`dto/AlarmSiteVO`；`AlarmMapper` 新增 `findActiveBySiteId`；`AiController` 新增端点；`OperationLogAspect` 支持 `{ret}` 占位符；
- 迁移：`V10__function_call_operation_type.sql`（`chk_operation_type` 增加 `FUNCTION_CALL`）；H2 测试 schema 同步；
- 测试：`DeviceAiToolsTest`（工具 JSON + 站点作用域）、`DeviceStatusAgentServiceTest`（循环/硬限/未参考实时数据/fail-fast）、`OperationLogAspectTest`（`{ret}` 审计）；`FlywayProductionSeedIsolationTest` 迁移清单加入 V10；
- 文档：路线图 Week 10 Day 68、架构文档、AGENTS §3、0012 changelog V10、sql/README、本 ADR、Day 68 日志。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| Spring AI 自动工具循环无上限 | 每轮显式 `internalToolExecutionEnabled(false)`，工具执行收口到本服务手动循环，轮次/调用数精确可控 |
| 工具被模型重复调用（token 消耗放大） | 3 轮硬限 + 单次请求 timeout（沿用 `DeepSeekProperties.timeoutSeconds`）；工具为只读查询，无副作用 |
| `response_format=json_object` 默认选项影响问答输出 | `OpenAiChatModel.createRequest` 仅采用 Prompt 级 options（模型默认 options 由 ChatClient 层合并），Agent 每轮显式构造 `OpenAiChatOptions` 不含 responseFormat，输出为自然语言 |
| 工具返回 JSON 过大占用上下文 | 列表工具默认 limit（5/10，上限 20），字段精简约简 |
| 模型编造数据 | 系统提示词强制「先调工具再回答、不得臆造」；`referencedRealTime` 供前端明示数据来源 |

---

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
