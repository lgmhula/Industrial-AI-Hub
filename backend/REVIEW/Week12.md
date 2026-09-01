# Week 12 复盘 — Agent + MCP（Phase 4 第 12 周）

> 日期：2026-08-30 | 覆盖：Day 78 \~ Day 84
> 基线演进：227 → 269 tests（+42）；ADR 0026 → 0030（5 条决策）

***

## 一、本周目标 vs 实际

| 目标                            | 实际                                                                                                          |  状态 |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------- | :-: |
| Day 78: Agent 概念 + ReAct 循环治理 | ADR 0026：手动循环、3 轮硬限、只读工具、可观测性五件套                                                                            |  ✅  |
| Day 79: 多步推理 Agent            | 提取通用 `ToolCallingAgent` + `DeviceAnalysisAgentService` + `/api/ai/agents/device-analysis`（4 轮）              |  ✅  |
| Day 80: MCP Server + SSE      | `spring-ai-starter-mcp-server-webmvc` + 4 只读工具 + `McpToolConfig` 显式注册边界（ADR 0027）                           |  ✅  |
| Day 81: MCP 数据工具              | 3 个数据查询工具：时间范围 / 聚合统计 / 动态搜索（ADR 0028），共 7 工具                                                               |  ✅  |
| Day 82: MCP 客户端 + 传输鉴权        | MCP Java SDK 0.10.0 + `McpClientService` 三步冒烟 + `McpAccessFilter` X-MCP-Token（ADR 0029）                     |  ✅  |
| Day 83: Agent + MCP 联调        | `McpInspectionAgentService` 单会话巡检 + `McpToolCallbackAdapter` + `/api/ai/agents/inspection-report`（ADR 0030） |  ✅  |
| Day 84: 周复盘 + 笔记              | 本复盘 + `agent-learning-notes.md` / `mcp-learning-notes.md` 更新                                                |  ✅  |

## 二、关键收获

### 2.1 关闭自动工具循环，是 Agent 可控的第一步

Spring AI 默认让模型自动反复调用工具直到自然结束，这对工业场景太危险——
token 成本、超时、死循环都不可控。`ToolCallingAgent` 在每轮 `OpenAiChatOptions`
显式置 `internalToolExecutionEnabled(false)`（[ToolCallingAgent.java#L118](../src/main/java/dev/reboot/agent/ToolCallingAgent.java#L118)），
把"模型请求工具 → 执行 → 回填对话 → 再调用"改成手动循环，轮次硬限 + `forceFinalize`
强制收尾，让 Agent 行为在预算内可终止、可审计。

### 2.2 通用循环是一次"泛化投资"，巡检复用零改造成本

Day 78 把 ReAct 循环写死在 `DeviceStatusAgentService`，Day 79 提取出
`ToolCallingAgent.run(systemPrompt, userPrompt, toolContext, toolCallbacks, maxRounds)`
通用签名。Day 83 的巡检 Agent 什么都不用改——只换 system prompt、换工具来源
（MCP 适配器代替内部 @Tool）、把硬限从 3 轮放宽到 6 轮（ADR 0026 §2③ 显式允许多步场景）。
如果当初不泛化，巡检就得再抄一遍循环逻辑，治理字段（trace/truncated）双份维护。

### 2.3 MCP 暴露边界：自动收集是坑，显式注册是药

Spring AI MCP Server 自动收集容器内**所有** `ToolCallbackProvider`。但 Day 68/79 的
内部 Agent 工具依赖 `ToolContext.userId` 做站点授权（ADR 0020），而 MCP 1.0 未定义
鉴权头、传输不透传 Header——自动收集会让内部工具在无身份下全量 403，甚至越权。
ADR 0027 用 `McpToolConfig` 的 `MethodToolCallbackProvider.builder().toolObjects(mcpDeviceTools)`
**显式只注册 MCP 专用工具类**，内部 Agent 工具不漏给外部客户端。这是跨信任域暴露的
第一原则：暴露什么必须显式，不能靠"自动"。

### 2.4 MCP 1.0 鉴权空白，用共享令牌门填补

MCP 1.0 规范没有 HTTP 鉴权，Spring AI 也不透传 Header 到工具上下文。ADR 0029 没有等
协议演进，而是用 `McpAccessFilter` 在 `/mcp/sse` 与 `/mcp/message` 前置一道**可选共享令牌门**：
配置 `app.mcp.access-token` 非空时必须携带一致 `X-MCP-Token`，比较用 `MessageDigest.isEqual`
常量时间防侧信道。本地空 = 内网可信直连，不加配置负担；公网必启。RBAC 仍不进 MCP 通道，
权限收敛在 JWT REST 入口（巡检只给 ADMIN）。

### 2.5 联调的关键不是"调通"，而是"会话复用 + 错误降级"

Day 83 巡检要调 10+ 次工具。如果每次重连 SSE，握手成本与失败面都放大。ADR 0030 用
`McpInspectionSession`（`AutoCloseable`）让一次巡检复用同一个 `McpSyncClient`，工具清单
一次性适配为 `ToolCallback[]`。更关键的是 `McpToolCallbackAdapter` 在工具返回 `isError`
或抛异常时降级为 `{"error":"..."}` JSON，**不中断 Agent 循环**，让模型在日报里如实说明
受限项——这与内部 Agent 工具的失败语义完全一致（ADR 0026 §2⑥），是两个生态能拼在一起
的真正原因。

## 三、Week 12 演进全景

```text
Day 78   概念 + 治理           ADR 0026：ReAct 手动循环 / 3 轮硬限 / 可观测性
Day 79   通用循环 + 多步 Agent  ToolCallingAgent ← DeviceStatusAgentService 泛化
                            DeviceAnalysisAgentService（4 轮）/api/ai/agents/device-analysis
Day 80   MCP Server          McpDeviceTools（4 只读）+ McpToolConfig 显式边界（ADR 0027）
Day 81   MCP 数据工具         +3 工具：range/stats/search（ADR 0028），共 7 工具
Day 82   MCP 客户端           McpClientService 三步冒烟 + X-MCP-Token 令牌门（ADR 0029）
Day 83   Agent + MCP 联调     McpInspectionSession 单会话 + McpToolCallbackAdapter
                            + McpInspectionAgentService（6 轮）/inspection-report（ADR 0030）
```

**端点演进**：

| 端点                                         | 角色      | 工具来源     | 硬限  | 审计                |
| ------------------------------------------ | ------- | -------- | --- | ----------------- |
| `/api/ai/agents/device-analysis`（Day 79）   | VIEWER+ | 内部 @Tool | 4 轮 | FUNCTION\_CALL/AI |
| `/api/mcp/smoke`（Day 82）                   | ADMIN   | MCP 直接调  | 单次  | MCP\_SMOKE/MCP    |
| `/api/ai/agents/inspection-report`（Day 83） | ADMIN   | MCP 经适配器 | 6 轮 | INSPECTION/MCP    |

## 四、第四阶段第 12 周检查点

> 目标：让 AI 从"单次问答"走向"多步 Agent + 跨协议工具生态"，并打通 Agent 与 MCP 的联调闭环。

| 能力             | 证据                                                                        |
| -------------- | ------------------------------------------------------------------------- |
| 多步推理 Agent     | `ToolCallingAgent.run`（轮次硬限 + trace + forceFinalize）                      |
| 内部 Agent 工具    | `DeviceAiTools`（get\_device\_basic / list\_device\_recent\_data / alarms） |
| MCP Server     | `/mcp/sse` + `/mcp/message`，7 只读工具（McpDeviceTools）                        |
| MCP 客户端        | `McpClientService.smoke`（SSE 握手 + listTools + 只读探针）                       |
| MCP 传输鉴权       | `McpAccessFilter` X-MCP-Token 常量时间比较                                      |
| Agent + MCP 联调 | `McpInspectionAgentService` 单会话巡检 + 适配器 + 6 轮硬限                           |
| 审计闭环           | Flyway V12（MCP\_SMOKE/MCP）+ V13（INSPECTION）；`@OperationLog` 三端点全覆盖        |

**检查点结论**：Agent 从"概念循环"走到"跨协议工具联调"，MCP 从"Server 暴露"走到
"客户端联调 + 传输鉴权"，AI 能力从"被动问答"升级为"主动巡检 + 生成日报"。

## 五、技术债务状态

### 5.1 Week 12 期间已解决

| #      | 问题                                            | 解决于                                                                                |
| ------ | --------------------------------------------- | ---------------------------------------------------------------------------------- |
| DG-001 | `.env.example` 缺 MCP 3 变量模板（ADR 0015 §8.3 不符） | Day 83（补 MCP\_ACCESS\_TOKEN / MCP\_CLIENT\_BASE\_URL / MCP\_CLIENT\_SSE\_ENDPOINT） |
| DG-002 | `POST /api/mcp/smoke` 无 `@OperationLog`       | Day 83（`@OperationLog(MCP_SMOKE/MCP)` + Flyway V12）                                |

### 5.2 Week 12 引入但未登记的潜在债务（建议补 TECH-DEBT.md）

| 项                                | 影响                    | 建议                                                         |
| -------------------------------- | --------------------- | ---------------------------------------------------------- |
| `app.mcp.access-token` 空默认（内网可信） | 公网误开放将无鉴权             | 与 TD-024 同性质，建议登记 P1：启动期 `enabled && token.isBlank()` WARN |
| 巡检 6 轮硬限未用真实 DeepSeek 端到端验证      | 大量设备时是否够用未知           | 接真实 Key 后做一次 ≥10 台设备的端到端冒烟                                 |
| MCP 工具无站点级授权                     | MCP 通道全量只读，跨站点无隔离     | 沿用 ADR 0027 语义，待 MCP 1.1 OAuth 后补                          |
| 前端无巡检日报入口                        | 管理员仍需 Postman/curl 触发 | Day 85+ 路线图内补展示/推送                                         |

### 5.3 既有债务延续

| ID     | 说明                                     | 状态                                    |
| ------ | -------------------------------------- | ------------------------------------- |
| TD-024 | DeepSeekProperties api-key 空默认无启动 WARN | ⏳ P1（与 MCP token 同类，可一并处理）            |
| TD-030 | V9 MySQL IT 缺口（历史）                     | ⏳ P2                                  |
| —      | 哈希 embedding 语义弱 / 内存向量库不持久化           | ⏳ Week 11 遗留，接真实 embedding/Qdrant 时替换 |

## 六、不足与改进

1. **Agent 端到端未用真实 DeepSeek Key 验证**：单测覆盖 503/降级/forceFinalize 路径，
   但"6 轮能否巡检 ≥10 台设备并产出有效日报"仍是未知数，需真实 Key 做一次端到端冒烟；
2. **MCP 传输鉴权是自定义令牌，非标准协议**：`X-MCP-Token` 是临时方案，MCP 1.1 OAuth
   就绪后应替换为标准协议，不长期手造；
3. **巡检日报无前端入口与推送**：当前只能 ADMIN 手动 curl 触发，日报一次性返回；
   Day 85 路线图要求经 RabbitMQ 推送到前端，Day 86 要求 AI 异常自动生成告警闭环；
4. **MCP 工具无站点隔离**：内网可信前提下可接受，但跨站点租户场景需要等协议演进
   或在工具层补可选的站点过滤参数；
5. **6 轮硬限是经验值**：未基于真实 token 成本/延迟做调参，未来可做成 `McpProperties`
   可配置项，与 ADR 0026 §2③ 的"多步场景显式放宽"语义一致。

## 七、下周展望（Week 13：AI 模块打磨）

| 天         | 任务                                             |
| --------- | ---------------------------------------------- |
| Day 85    | AI 生成日报经 RabbitMQ 自动推送到前端                      |
| Day 86    | AI 巡检异常自动生成报警——AI 与业务闭环                        |
| Day 87-91 | AI 模块打磨（日报展示页 / 告警闭环 / 监控埋点等，见 DAILY\_ROADMAP） |

> Week 12 收官。Agent + MCP 的"概念 → Server → 客户端 → 联调"全链路打通，
> 下一阶段从"能跑通"走向"自动推送 + 业务闭环"。

