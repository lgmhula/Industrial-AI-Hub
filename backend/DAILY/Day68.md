# Day 68 — Phase 4 AI 集成：Spring AI @Tool 声明式 Function Calling（设备状态问答 Agent）

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 10
> **分支**：`main`（codex 直接介入本分支；已由验证 Agent 验收）
> **配套 ADR**：[0023-function-calling.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0023-function-calling.md)
> **依赖 ADR**：[0022-spring-ai-chatclient.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0022-spring-ai-chatclient.md)（Spring AI ChatClient 抽象）、[0020-site-resource-scope.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0020-site-resource-scope.md)（工具数据访问站点校验）
> **验收结果**：✅ **GO**（验证 Agent：208 tests 0 failures 0 skipped + AI Agent API 冒烟 6/6 + operation\_log FUNCTION\_CALL 写入 2/2 + 前端 build 820ms 0 errors + 浏览器 DeviceDetail 0 console SEVERE）

> **文档补录说明**：codex 执行本任务时未写入 Day 日志文件（历次会话通病，TD-028/029/030 同类根因），本文件由验证 Agent 在验收阶段按实际交付内容补录，严格对齐 AGENTS §4.3 文档同步 + 审计追溯要求。

***

## 一、交付范围（对照 ADR 0023 §2 全部达成）

| # | ADR 0023 决策项                                                                                                                                                                           | 交付结果          | 证据                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| - | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | 工具协议：Spring AI `@Tool` / `@ToolParam` 声明式注册，零手写 JSON Schema，`ToolCallbacks.from(bean)` → `ToolCallback[]`                                                                              | ✅ 声明式         | [DeviceAiTools.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/tool/DeviceAiTools.java) L66/L91/L114 三处 `@Tool(name + description)`；[DeviceStatusAgentService.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/service/DeviceStatusAgentService.java) L93 `this.toolCallbacks = ToolCallbacks.from(deviceAiTools)` 一行完成注册                                                      |
| 2 | 工具集（最小 3 个）：`get_device_basic` / `list_device_recent_alarms` / `list_active_alarms_by_site`，集中于 `dev.reboot.tool` 包                                                                    | ✅ 3 工具        | DeviceAiTools L66 `getDeviceBasic` 基础信息、L91 `listDeviceRecentAlarms` 设备最近告警、L114 `listActiveAlarmsBySite` 站点未处理告警；AlarmMapper 新增 [findActiveBySiteId](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/mapper/AlarmMapper.java) L35-L41 JOIN device 过滤 d.is\_deleted=0                                                                                                                                                                                                                                                  |
| 3 | 新接口：`POST /api/ai/agents/device-status`（`AiDeviceStatusRequest{deviceId, question}` → `AiDeviceStatusResult{answer, toolRounds, toolCalls, referencedRealTime, truncated, toolTrace}`） | ✅ 契约对齐        | AiController L79-L86 端点；DTO [AiDeviceStatusRequest.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/dto/ai/AiDeviceStatusRequest.java)（@NotNull deviceId / @NotBlank question / @Size 2000）；[AiDeviceStatusResult.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/dto/ai/AiDeviceStatusResult.java)（7 字段 + `AiToolCallTrace{toolName, success}` 列表 + 紧凑 toString() 供 {ret} 审计） |
| 4 | Agent 循环：系统提示词 + 设备上下文预置 → 「模型请求工具 → 手动执行 → 结果回填」；每轮 `internalToolExecutionEnabled(false)` 关闭 Spring AI 默认自动循环                                                                         | ✅ 手动循环        | DeviceStatusAgentService L62-L73 SYSTEM\_PROMPT + L112-L116 预置设备 ID/名称/编码/站点 ID；L194-L205 `toolOptions()` 显式 `internalToolExecutionEnabled(false)`；while 循环 L126-L167：「chatModel.call → hasToolCalls? → 执行 callback.call → ToolResponseMessage 回填」                                                                                                                                                                                                                                                                                                                                                                                                  |
| 5 | 轮次硬限：最大 **3 轮**工具调用；达到硬限后追加收尾提示无工具调用一次，结果标注 `truncated=true`                                                                                                                           | ✅ 3 轮硬限       | DeviceStatusAgentService `MAX_TOOL_ROUNDS = 3`（L58）；L132-L138 `rounds >= 3` 分支调用 `forceFinalize(conversation)`；L171-L176 收尾函数追加 FINALIZE\_HINT 并 `toolOptions(Map.of(), false)` 不带工具再调用一次                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 6 | 未参考实时数据：模型未调用任何工具直接回答 → `referencedRealTime=false`，前端标注「未参考实时数据」                                                                                                                       | ✅ 显式标注        | L129 `rounds > 0` 即 referencedRealTime=true；反之为 false；前端 DeviceDetail.vue L103-L104 `el-tag type=warning` 对应 warning 渲染                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 7 | 站点作用域：当前用户 ID 经 `ToolContext` 传入工具，工具内 `SiteAccessService.assertSiteAccess(VIEWER)`；403/404 返回 `{"error": "..."}` JSON                                                                 | ✅ ADR 0020 对齐 | DeviceAiTools L48 `CONTEXT_USER_ID = "userId"`；L146-L149 `assertViewerAccess(siteId, toolContext)` 提取 userId 并走 `siteAccessService.assertSiteAccess(..., RoleEnum.VIEWER)`；BusinessException 被 catch → `errorJson(message)` L214-L219 返回 `{"error":"无权访问该站点资源"}` 形式，日志 WARN: "AI 工具拒绝/失败" 预期输出（测试日志确认）                                                                                                                                                                                                                                                                                                                                              |
| 8 | 审计：Flyway V10 扩展 `chk_operation_type` 允许 `FUNCTION_CALL`；端点 `@OperationLog(operationType="FUNCTION_CALL", targetType="AI", description="{ret}")`；`OperationLogAspect` 支持 `{ret}` 占位符替换 | ✅ 审计闭环        | Flyway [V10\_\_function\_call\_operation\_type.sql](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/resources/db/migration/V10__function_call_operation_type.sql)；`AiController` L79 `@OperationLog(operationType="FUNCTION_CALL", targetType="AI", description="AI 设备状态问答（工具调用） {ret}")`；`OperationLogAspect` L121-L146 `buildDescription` 新增 `{ret}` 替换逻辑 + `formatResult` 400 字截断（对齐 description varchar(512) 上限）                                                                                                                   |

> Spring AI 自动循环风险规避（ADR 0023 §5）已验证：DeviceStatusAgentServiceTest 3 轮硬限场景触发 `WARN "AI 工具调用达到 3 轮硬限，强制收尾"` 日志（测试输出第 3 行命中，DeviceStatusAgentServiceTest 6/6 全绿）。

***

## 二、新增/修改文件清单（17 个文件 = 代码 7 + 测试 4 + DTO 3 + 前端 1/改 2 + 迁移 1 + ADR 1）

### 2.1 Java 后端（新增 6 + 修改 3）

| 分层             | 文件                                                                                                                                                                                                                                                            | 变更摘要                                                                                                                                                                                                                              |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **tool**（新增包）  | [DeviceAiTools.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/tool/DeviceAiTools.java)（全新 220 行）                          | `@Component` + 3 × `@Tool`（get\_device\_basic / list\_device\_recent\_alarms / list\_active\_alarms\_by\_site）；构造器注入 4 依赖；内部 requireDevice / assertViewerAccess / userIdFrom(ToolContext) / clampLimit(1-20) / toJson / errorJson |
| Service（新增）    | [DeviceStatusAgentService.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/service/DeviceStatusAgentService.java)（全新 240 行） | `@Service`；`MAX_TOOL_ROUNDS = 3`；构造器注入 ChatModel/DeepSeekClient/Properties/DeviceMapper/SiteAccessService/**DeviceAiTools** → `ToolCallbacks.from`；`answer()` 循环 + `forceFinalize()` 收尾；`buildResult()` 封装 7 字段 DTO               |
| Controller（修改） | [AiController.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/controller/AiController.java) L78-L86                        | 构造器新增 `DeviceStatusAgentService deviceStatusAgentService`；新增 `POST /agents/device-status`；`@RequireRole(VIEWER/OPERATOR/ADMIN)`；`@OperationLog(FUNCTION_CALL, AI, description 含 {ret})`                                           |
| AOP（修改）        | [OperationLogAspect.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/aop/OperationLogAspect.java) L121-L166                 | `buildDescription` 新增 {ret} 分支（L133-L135）；新增 `formatResult`：null→"null"，字符串或 toString 后截断 400 字（<= description varchar(512) 容限）；`formatArg` 保留原有反射提取 id 逻辑不变                                                                      |
| Mapper（修改）     | [AlarmMapper.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/mapper/AlarmMapper.java) L35-L41                              | 新增 `List<AlarmSiteVO> findActiveBySiteId(Long siteId, int limit)`；JOIN device 取 device\_name；`a.status = 0` 仅未处理；`d.is_deleted = 0` 不看逻辑删除设备；ORDER BY triggered\_at DESC LIMIT                                                    |
| DTO ai（全新）     | AiDeviceStatusRequest / AiDeviceStatusResult / AiToolCallTrace（3 类 见 §2.2）                                                                                                                                                                                    | 请求/结果/工具轨迹三类 DTO 齐全                                                                                                                                                                                                               |
| DTO 业务（全新）     | [AlarmSiteVO.java](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/java/dev/reboot/dto/AlarmSiteVO.java)（40 行）                                   | 站点活动告警载体：id/deviceId/**deviceName**/alarmType/alarmLevel/alarmMessage/status/triggeredAt；deviceName 让 AI 工具输出即可被模型理解归属设备                                                                                                          |

### 2.2 DTO（3 × 新增 = 2 DTO ai + 1 VO）

| 文件                           | 字段                                                                                              | 校验/用途                                                                   |
| ---------------------------- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `AiDeviceStatusRequest.java` | deviceId: Long, question: String                                                                | @NotNull deviceId / @NotBlank question / @Size(max=2000)；参校失败 400（冒烟已证） |
| `AiDeviceStatusResult.java`  | deviceId, answer, toolRounds, toolCalls, referencedRealTime, truncated, `List<AiToolCallTrace>` | toString() 紧凑格式对齐 {ret} 占位符；buildResult 兜底 answer 空→"AI 未返回有效回答"；       |
| `AiToolCallTrace.java`       | toolName, success                                                                               | 前端透明轨迹展示：t.success? → 绿 tag / 红 tag ✗；DeviceDetail.vue L108-L111 渲染     |

### 2.3 数据库迁移（Flyway V10）

| 文件                                                                                                                                                                                                                                                                      | 审计要点                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [V10\_\_function\_call\_operation\_type.sql](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/backend/src/main/resources/db/migration/V10__function_call_operation_type.sql) | `chk_operation_type` 先 DROP 再 ADD 11 取值：CREATE/UPDATE/DELETE/LOGIN/EXPORT/ACKNOWLEDGE/RESOLVE/CHAT/SUMMARY/DIAGNOSE/**FUNCTION\_CALL** ✅ 完整覆盖；H2 测试 schema `schema-h2.sql:123` 同步 11 取值；生产库 reboot 重启后成功由 version 9 → 10（启动日志：DbMigrate `Migrating schema reboot to version "10 - function call operation type"` → `Successfully applied 1 migration ... now at version v10` ✅）；FlywayProductionSeedIsolationTest L64 迁移清单 V10 ✅ |

### 2.4 Java 测试（4 × = 新增 15 tests；合计 208/0/0）

| 文件                                               | 新增数                    | 覆盖要点                                                                                                                                                                                                                                                                                                                                                                             | 验证结果                                                                            |
| ------------------------------------------------ | ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `DeviceAiToolsTest.java`（全新）                     | **7**                  | ① get\_device\_basic 成功 JSON 含 id/deviceName/statusLabel/ipAddress ② get\_device\_basic 站点拒绝 → {"error":"无权访问该站点资源"} ③ get\_device\_basic 设备不存在 → {"error":"设备不存在"} ④ list\_device\_recent\_alarms clamp limit=0→1 5 list\_device\_recent\_alarms clamp limit=100→20 6 list\_active\_alarms\_by\_site 成功 JOIN 带 deviceName 7 userIdFrom ToolContext Number→Long / String→Long 转换 | ✅ 7/7（Day 测试日志 WARN: "无权访问该站点资源" ×2 + "设备不存在" ×1 全部预期）                          |
| `DeviceStatusAgentServiceTest.java`（全新）          | **6**                  | ① ensureAvailable 未启用 → fail-fast 503（never call chatModel）② 模型首轮直接回答无工具 → referencedRealTime=false, rounds=0, calls=0 ③ 模型请求 1 轮工具成功 → referencedRealTime=true, rounds=1, trace 长度正确 ④ 模型请求未知工具 no\_such\_tool → trace 含 success=false ⑤ 3 轮硬限：4 次连续请求工具 → truncated=true + WARN 日志命中 "达到 3 轮硬限，强制收尾" ⑥ requireDevice 不存在 → 404（before any toolCallback registration）           | ✅ 6/6（测试日志 "AI 请求未知工具: no\_such\_tool" + "AI 工具调用达到 3 轮硬限，强制收尾（deviceId=1）" 命中） |
| `OperationLogAspectTest.java`（+2）                | **2**                  | ① {ret} 正常替换：返回 AiDeviceStatusResult → description 包含 "rounds=2, calls=3, realtime=true" ② {ret} 失败场景：方法抛 BusinessException → description 以 `[失败] ... null` 结尾且不抛 DataIntegrityViolation                                                                                                                                                                                         | ✅ 2/2                                                                           |
| `FlywayProductionSeedIsolationTest.java`（迁移清单对齐） | **0**（原有单测断言包含 V10）    | L63 枚举含 V9 + L64 `V10__function_call_operation_type.sql`；V1-V10 白名单精确；生产迁移目录 10 个文件 = set 比较 **精确无遗漏无多余**                                                                                                                                                                                                                                                                        | ✅ 通过                                                                            |
| **合计**                                           | **+15**（193 → **208**） | —                                                                                                                                                                                                                                                                                                                                                                                | **✅ 208/0/0（Tests/Failures/Skipped）**                                           |

### 2.5 前端 Vue 3（= 1 API 修改 + 1 DeviceDetail 问答面板）

| 文件                                                                                                                                                                                                                                   | 变更摘要                                                                                                                                                                                                                                                                                                           | <br />        | <br />                                                                                                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [api/index.js](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/api/index.js) L119                                           | 新增 `aiApi.deviceStatus = (deviceId, question) => api.post('/ai/agents/device-status', { deviceId, question }, { timeout: 60000 })`；与 Day67 三个 AI 方法（60s 超时）保持一致                                                                                                                                                | <br />        | <br />                                                                                                                                                           |
| [DeviceDetail.vue](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/frontend/src/views/DeviceDetail.vue) L90-L118 + L178-L183 + L282-L299 | ① 新增 **独立折叠卡片**（Day 67 AI 诊断卡下方）：`el-collapse` 包裹「AI 设备问答（自动查询实时数据）」标题；顶部 qa-tip 告知「最多 3 轮工具调用」② qa-meta 4 类 tag：referencedRealTime (success/warn) / toolCalls×rounds (info) / truncated 警告黑 / toolTrace 逐项 success?green:danger ③ askQuestion 函数：空提问 ElMessage.warning；三态 qaLoading/qaError/qaResult；res.data | <br />        | res 兼容 axios 与非 axios 包；60s timeout 错误由 api/index.js 控制；浏览器冒烟：输入「这台设备最近状态怎么样？」→ 点击提问 → 显示错误 Banner「DeepSeek AI 服务未启用，请配置 DEEPSEEK\_ENABLED=true」+ qaLoading 解除 ✅ |
| 前端构建                                                                                                                                                                                                                                 | `npm run build` → built in 820ms；0 errors；0 warning 仅 `chunk > 500 kB`（非功能性，与 Day67 相同）                                                                                                                                                                                                                        | ✅ 前端 0 errors | <br />                                                                                                                                                           |

***

## 三、Day67 → Day68 架构演进对照

```
┌───────────────────────────────────────────────────────────────────────┐
│ AiController.java                                                     │
│   POST /ai/chat                  (CHAT,        targetType=AI) Day66  │
│   POST /ai/alarms/{id}/summary   (SUMMARY,     targetType=AI) Day67  │
│   POST /ai/devices/{id}/diagnose (DIAGNOSE,    targetType=AI) Day67  │
│   POST /ai/agents/device-status  (FUNCTION_CALL targetType=AI) Day68 │
│        │  @OperationLog description = "... {ret}"                    │
│        └───────────────────► OperationLogAspect.buildDescription()   │
│                              {ret} ──► formatResult → 400 字截断     │
│                                                                       │
├───────────────────────────────────────────────────────────────────────┤
│ Service Layer                                                         │
│                                                                       │
│   AiService.java (Day67)                                              │
│     chat()  ──── DeepSeekClient                                       │
│     summarizeAlarm() / diagnoseDevice()                               │
│                └── ChatClient + PromptTemplate                       │
│                                                                       │
│   DeviceStatusAgentService.java ⭐ 新增（Day68）                      │
│     answer():                                                         │
│       [1] ensureAvailable()     ◄── fail-fast（503 先于任何调用）    │
│       [2] requireDevice + assertSiteAccess(VIEWER, userId)            │
│       [3] 对话：SYSTEM_PROMPT + UserMessage(设备上下文预置)           │
│       [4] while 循环 ─── MAX_TOOL_ROUNDS=3 ────────────────┐         │
│             ├─ chatModel.call(Prompt(conversation, toolO  │         │
│             │                   ptions(withTools=true)))   │         │
│             ├─ if !hasToolCalls → return final             │         │
│             ├─ if rounds>=3 → forceFinalize() → truncated  │         │
│             └─ else: rounds++                               │         │
│                 └─ for toolCall in assistant.toolCalls     │         │
│                      │  ToolCallback.call(args, ToolCtx)    │         │
│                      └─ build ToolResponseMessage          │         │
│                 conversation.add(assistant, toolResp)      │         │
│                 back to step [4]  ─────────────────────────┘         │
│                                                                       │
├───────────────────────────────────────────────────────────────────────┤
│ 工具层（dev.reboot.tool 新增包）                                      │
│  DeviceAiTools @Component                                             │
│    @Tool get_device_basic(deviceId, ToolCtx)    → JSON                │
│    @Tool list_device_recent_alarms(deviceId,limit, ToolCtx) → JSON    │
│    @Tool list_active_alarms_by_site(siteId,limit, ToolCtx) → JSON     │
│      └─ 每工具内部：requireDevice / assertSiteAccess(VIEWER)           │
│         BusinessException 捕获 → return {"error":"..."} 让模型解释   │
│                                                                       │
│  ToolCallbacks.from(deviceAiTools) → ToolCallback[]                   │
│  注册到 DeviceStatusAgentService.toolCallbacks（构造器一次）          │
│                                                                       │
├───────────────────────────────────────────────────────────────────────┤
│ 数据库层（Flyway V10 / H2 schema 同步）                               │
│  operation_log.chk_operation_type：11 取值（+FUNCTION_CALL）         │
│  实际 @OperationLog 集合 vs CHECK 集合：精确相同 11/11                │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```

***

## 四、验收结果（验证 Agent 执行）

### 4.1 后端 Tests：208 / 0 / 0 ✅

```
Total tests: run=208, failures=0, errors=0, skipped=0
报告文件 27 份（27 test classes）
新增 15 tests：
  DeviceAiToolsTest                 7/7
  DeviceStatusAgentServiceTest      6/6
  OperationLogAspectTest （增量）    2/2
WARN 日志（预期 fallback / 模拟场景）：
  · "AI 工具拒绝/失败: 无权访问该站点资源" × 2
  · "AI 工具拒绝/失败: 设备不存在"
  · "AI 请求未知工具: no_such_tool"
  · "AI 工具调用达到 3 轮硬限，强制收尾（deviceId=1）"
  · "告警摘要 JSON 解析失败，退回纯文本"（Day67 AiService fallback，无回归）
  · "DeepSeek API 调用失败: HTTP 401 UNAUTHORIZED"（Day66 单测模拟 API key 无效）
```

### 4.2 AI Agent API 冒烟（curl 实机 6/6）

| # | 场景                                                                     | 预期                                                      | 实际                                                                                                                 |  通过 |
| - | ---------------------------------------------------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | :-: |
| 1 | POST 无 JWT /api/ai/agents/device-status                                | HTTP 401 + 消息 "请先登录"                                    | HTTP 401 code=401 msg=请先登录                                                                                         |  ✅  |
| 2 | POST VIEWER JWT + DEEPSEEK\_ENABLED=false（.env 未配置）                    | HTTP 503 + 消息含「未启用/未配置/第三方」                             | HTTP 503 code=503 msg="DeepSeek AI 服务未启用，请配置 DEEPSEEK\_ENABLED=true"                                               |  ✅  |
| 3 | POST 合法 JWT body={question, deviceId=} deviceId 缺失                     | HTTP 400 + 参数校验失败                                       | HTTP 400 code=400 msg="参数校验失败: deviceId: 设备 ID 不能为空"                                                               |  ✅  |
| 4 | POST 合法 JWT question="   "（空白）                                         | HTTP 400                                                | HTTP 400 code=400 msg="参数校验失败: question: 问题不能为空"                                                                   |  ✅  |
| 5 | POST question.length = 2001                                            | HTTP 400                                                | HTTP 400 code=400 msg="参数校验失败: question: 问题不能超过 2000 字"                                                            |  ✅  |
| 6 | 场景 2（503 fail-fast）→ 后端 operation\_log 是否写入 FUNCTION\_CALL？（验证：失败也要审计） | INSERT 成功且 operation\_type='FUNCTION\_CALL' 不被 CHECK 拒绝 | id=104,105 两条成功：operation\_type=FUNCTION\_CALL target\_type=AI user\_id=1 description="\[失败] AI 设备状态问答（工具调用） null" |  ✅  |

### 4.3 数据库 Flyway V10 + 审计记录 ✅

```
生产库重启日志：
  DbValidate: Successfully validated 9 migrations（版本 1~9，重启前 schema 9）
  DbMigrate:  Current version of schema reboot: 9
  DbMigrate:  Migrating schema reboot to version "10 - function call operation type"
  DbMigrate:  Successfully applied 1 migration ... now at version v10

H2 测试 schema-h2.sql L123：
  FUNCTION_CALL 在 chk_operation_type 中（11 取值一致）

operation_log 审计（实际生产）：
  id=104: FUNCTION_CALL / AI / userId=1 / [失败] AI 设备状态问答（工具调用） null
  id=105: FUNCTION_CALL / AI / userId=1 / [失败] AI 设备状态问答（工具调用） null
  → 两条都成功插入，CHECK 约束 FUNCTION_CALL 允许值生效（无 DataIntegrityViolation）✅
```

### 4.4 前端 + 浏览器：DeviceDetail AI 三态 + 0 SEVERE ✅

| 项目                   | 结果                                                                                                                               |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 前端 build             | 820ms 0 errors（only chunk-size warn，非功能）                                                                                         |
| 浏览器进入 /devices/1     | 仪表盘 → 详情页成功渲染（登录页 → 仪表盘 → 详情 正常跳转）                                                                                               |
| AI 诊断卡（Day67）        | 保留 ✔，仍含「生成诊断」按钮                                                                                                                  |
| AI 设备问答折叠面板（Day68）   | 标题、qa-tip、输入框、提问按钮全部渲染 ✔                                                                                                         |
| 输入「这台设备最近状态怎么样？」→ 提问 | qaLoading=true → 请求返回 503 → qaError="DeepSeek AI 服务未启用，请配置 DEEPSEEK\_ENABLED=true" Banner 显示 → qaLoading 解除（输入框 / 按钮 re-enabled） |
| Console SEVERE 错误    | 0 条（browser\_console\_messages 结果为 none）                                                                                         |

***

## 五、技术债务（Day68 识别 → 当日修复）

| ID     | 类别     | 说明                                                                                                                                                                                | 状态   |
| ------ | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| TD-032 | 审计可读性  | OperationLogAspect 失败场景 `{ret}` = "null"，description 形如 `[失败] AI 设备状态问答（工具调用） null`，末尾 null 冗余且不友好                                                                 | ✅ 已修复（`formatResult` 改接收 `Throwable error`，失败时替换为异常消息；`around` 捕获异常传入 `recordLog`） |
| TD-033 | 失败前置顺序 | DeviceStatusAgentService `ensureAvailable()` 在资源校验之前，AI 未启用时 404/403 被 503 掩盖                                                                                             | ✅ 已修复（调整为 requireDevice → assertSiteAccess → ensureAvailable） |

> 修复验证：208 tests / 0 failures / 0 errors / 0 skipped（OperationLogAspectTest 2/2、DeviceStatusAgentServiceTest 6/6 全绿）。

***

## 六、明日计划（Day 69）

Week 10 路线图下一个工作项：**前端工业化视觉升级（DESIGN.md）**

| 优先级 | 内容                                                                                                  | 输入                                                                      | 验收                                                                          |
| :-: | --------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| ★★★ | 设计系统落地：全局 CSS 变量（主色/功能色/渐变/阴影/圆角/字号），Element Plus 主题定制覆盖（CSS var → el-$type），<body> / .app 背景、滚动条统一 | `frontend/src/DESIGN.md`、当前 AlarmList / DeviceDetail / Dashboard 三页作为样本 | Lighthouse 视觉一致性；三页 0 console SEVERE；1920×1080 / 1440×900 / 1280×720 三断点无错位 |
| ★★☆ | 表格/卡片工业化：.data-table 统一斑马纹 + sticky header；.card 统一渐变标题条 + 标题图标；空状态（EmptyState）对齐图标/颜色/间距           | Element Plus 表格样式                                                       | DeviceList / AlarmList / OperationLogList / UserList / RoleList 5 张表一致      |
| ★★☆ | AI 卡片样式统一：AI 诊断卡 + AI 问答折叠卡同色系（MagicStick 蓝紫渐变图标），工具轨迹 tag 与 DESIGN 色板对齐                            | Day67/68 两个 AI 模块                                                       | DeviceDetail 两张 AI 卡风格一致；referencedRealTime/truncated tag 对比度达标 WCAG AA     |
| ★☆☆ | 构建产物优化：code-splitting（Dashboard / EmptyState / 各 Views 动态 import），解决 "chunk > 500 kB" warning       | build chunkSizeWarningLimit                                             | build in <1.2s、chunk 500 kB 警告消失或至少下降 2 个 >500kB chunk                      |

> 备选（若 DESIGN.md 未准备好，退回 AI 主题继续）：DeviceStatusAgent 对话历史持久化 + 前端「最近提问」侧边栏；或者 Spring AI MCP（Multi-Capability Protocol）接入。

