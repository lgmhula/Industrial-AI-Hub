# Decision 0027: MCP 工具暴露边界（Week 12）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-29 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 80 / ADR 0023 / ADR 0026 / DeviceAiTools |

## 1. 背景

Day 80 引入 MCP（Model Context Protocol）Server，让外部 AI 客户端（Codex / Claude Desktop 等）
通过统一协议访问项目只读设备查询能力。Spring AI MCP Server 自动配置会收集容器内所有
`ToolCallbackProvider` / `ToolCallback` Bean 注册为 MCP tools。而 Day 68/79 已有的内部
Agent 工具（`DeviceAiTools`）依赖 `ToolContext.userId` 做站点资源作用域断言（ADR 0020），
MCP 1.0 规范未定义鉴权头，Spring AI 传输也未透传 HTTP Header，若自动暴露会导致：

1. 内部工具在无用户身份下抛 403，MCP 客户端拿到的全是错误；
2. 越权风险：无法做站点作用域隔离。

因此需要在引入 MCP Server 时同步界定工具暴露边界。

## 2. 决策

| 项 | 决策 |
|----|------|
| 传输方式 | **SSE**（WebMVC，`spring-ai-starter-mcp-server-webmvc`），不启用 stdio；排除 `McpWebFluxServerAutoConfiguration` 避免双路由 |
| 能力范围 | `capabilities` 仅 `tools: true`；`resources / prompts / completion` 全部 `false` |
| 工具集合 | 只暴露 `McpDeviceTools` 的 4 个只读查询工具，**不**暴露内部 Agent 工具 |
| 注册方式 | `McpToolConfig` 通过 `MethodToolCallbackProvider.builder().toolObjects(mcpDeviceTools)` 显式注册，阻断自动收集 |
| 工具语义 | 全部只读（查询），无副作用；返回 JSON 字符串 |
| limit 上限 | `clampLimit` 统一 clamp 到 `1-50`，默认值按场景区分（list=20 / data=10 / alarms=5） |
| 授权边界 | MCP Server 视为内部可信通道（内网/本机），当前工具无站点作用域断言 |
| 鉴权演进 | Day 82 MCP 客户端集成时引入传输层鉴权 + RBAC；当前仅内网可信 |
| 错误降级 | 缺 ID / 未知设备 / 业务异常统一返回 `{"error":"..."}` JSON，不中断 MCP 会话 |
| 客户端指引 | `instructions` 字段声明“只读、内网、勿暴露公网” |

### 2.1 为什么单独建一个工具类

`McpDeviceTools` 与 `DeviceAiTools` 功能重叠但职责不同：

- `DeviceAiTools`：面向内部 Agent，依赖 `ToolContext` 用户身份做站点授权；
- `McpDeviceTools`：面向外部 MCP 客户端，无身份，只暴露全量只读查询。

分离避免“一个工具两种鉴权语义”的歧义，也防止自动收集把带身份的工具漏给无身份通道。

### 2.2 为什么只开放 tools 能力

MCP 1.0 的 resources / prompts / completion 能力在本项目无对应数据源与场景，
开放只会增加攻击面与维护成本。`tools` 已能覆盖“查设备/查数据/查告警”目标。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| stdio 传输 | 需独立进程，与 WebMVC 单体冲突；SSE 复用 8080 端口更轻 |
| 自动收集所有 ToolCallback | 把依赖用户身份的内部 Agent 工具漏给无身份 MCP 客户端，越权 + 全量 403 |
| 复用 DeviceAiTools 暴露 | 单工具承担两种鉴权语义，边界模糊；MCP 通道无 userId 无法做站点作用域 |
| 开放 resources 能力 | 无对应只读资源视图，徒增攻击面 |

## 4. 影响与验证

- 代码：`McpDeviceTools` / `McpToolConfig` / `application.yml` MCP 段；
- 测试：`McpDeviceToolsTest` 8 用例覆盖 limit/默认上限/未知设备/缺 ID/数据/告警；
- 回归：后端 243 tests 0 failures（Day 81 治理修正，以 Day80 全量运行为准）；
- 文档：`docs/ai/mcp-learning-notes.md`、AGENTS/DAILY_ROADMAP 同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| MCP 端点被公网访问 | 当前内网可信；`instructions` 显式声明勿暴露公网；Day 82 加传输鉴权 |
| 工具返回 JSON 过大 | `clampLimit` 50 上限 + 字段精简（不返回创建时间等冗余字段） |
| 模型臆造数据 | 工具返回真实 DB 数据；MCP 客户端按返回 JSON 解读 |
| 内部工具被误暴露 | `McpToolConfig` 显式注册，阻断自动收集 |
| WebFlux/WebMVC 双路由 | `application.yml` 排除 `McpWebFluxServerAutoConfiguration` |

---

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
