# Week 12 MCP 学习笔记：协议、传输与工具暴露边界

> 日期：2026-08-29 | 覆盖：Day 80-81（ADR 0027 / ADR 0028）

---

## 1. MCP 是什么

MCP（Model Context Protocol）是一个让 LLM 客户端通过统一协议访问外部工具/资源的标准。
可以把它理解为“AI 版的 USB”：客户端（Codex / Claude Desktop）插上 Server，就能用同一套协议
调用 Server 暴露的工具，不用每个客户端单独写适配。

```text
MCP Client ──协议──> MCP Server ──> 你的业务（DB / API / 文件）
```

本项目里，MCP Server 就是 Spring Boot 应用本身（端口 8080），暴露只读设备查询工具，
让外部 AI 客户端能“查设备 / 查数据 / 查告警”。

## 2. SSE vs stdio

MCP 支持两种传输：

| | SSE | stdio |
|---|---|---|
| 通道 | HTTP + Server-Sent Events | 子进程标准输入输出 |
| 部署 | 复用 Web 端口，远程可达 | 本地子进程，单机 |
| 适用 | Web 应用、远程客户端 | 桌面客户端启动本地 Server |

本项目选 **SSE**：Spring AI 提供 `spring-ai-starter-mcp-server-webmvc`，直接在 8080 端口挂
`/mcp/sse` 与 `/mcp/message` 两个端点，无需独立进程。同时在 `application.yml` 排除
`McpWebFluxServerAutoConfiguration`，避免 WebFlux 与 WebMVC 双路由注册。

## 3. 工具暴露边界

Spring AI MCP Server 自动配置会收集容器内**所有** `ToolCallbackProvider` / `ToolCallback` Bean
注册为 MCP tools。这听起来方便，但有坑：Day 68/79 已有的内部 Agent 工具（`DeviceAiTools`）
依赖 `ToolContext.userId` 做站点授权（ADR 0020），而 MCP 1.0 规范未定义鉴权头，Spring AI
传输也不透传 HTTP Header——自动收集会导致内部工具在无身份下全量 403，甚至越权。

解法（ADR 0027）：单独建 `McpDeviceTools` 只读工具类，通过 `McpToolConfig` 用
`MethodToolCallbackProvider.builder().toolObjects(mcpDeviceTools)` **显式注册**，只暴露这一组：

- `mcp_list_devices`
- `mcp_get_device_basic`
- `mcp_list_device_recent_data`
- `mcp_list_device_recent_alarms`

内部 Agent 工具不会被自动漏给外部客户端。

Day 81（ADR 0028）在同一个工具类内补充数据查询能力，共 7 个只读工具：

- `mcp_get_device_data_range` — 按时间范围查询运行数据（dataType 可选）
- `mcp_get_device_data_stats` — 聚合统计 avg/min/max/count
- `mcp_search_devices` — 按关键字/类型/状态搜索设备

## 4. 与 Function Calling 的区别

| | Function Calling | MCP |
|---|---|---|
| 谁调用工具 | 应用进程内的模型 | 外部 MCP 客户端 |
| 工具实现位置 | 应用代码（@Tool） | MCP Server（也是应用代码，但走协议暴露） |
| 身份上下文 | 有（Controller 拿到 JWT userId 注入 ToolContext） | 无（MCP 1.0 未定义鉴权头） |
| 适合 | 应用内 Agent | 跨客户端复用工具能力 |
| 暴露边界 | 进程内，无需隔离 | 跨信任域，必须显式边界 |

一句话：Function Calling 是“模型调你的代码”，MCP 是“别人的客户端通过协议调你的代码”。
后者是跨信任域，所以暴露什么必须显式控制。

## 5. 项目落地

`application.yml` 关键配置：

```yaml
spring.ai.mcp.server:
  enabled: true
  name: industrial-ai-hub-mcp
  version: 1.0.0
  sse-endpoint: /mcp/sse
  sse-message-endpoint: /mcp/message
  capabilities: { tool: true, resource: false, prompt: false, completion: false }
```

工具返回统一是 JSON 字符串，错误降级为 `{"error":"..."}`，limit 用 `clampLimit` 夹到 1-50。
授权上当前视为内网可信通道，Day 82 客户端集成时再补传输层鉴权与 RBAC。

## 6. Day 81：数据查询工具契约

| `@Tool` name | 参数 | 底层能力 |
| --- | --- | --- |
| `mcp_get_device_data_range` | `deviceId, dataType?, startTime?, endTime?, limit?` | `DeviceDataMapper.findByTimeRange` |
| `mcp_get_device_data_stats` | `deviceId, dataType, startTime?, endTime?` | `DeviceDataMapper.aggregate`（`cnt` 归一化为 `count`） |
| `mcp_search_devices` | `keyword?, deviceType?, status?, limit?` | `DeviceMapper.searchDevices`（无站点过滤） |

约定：

- `limit` clamp 到 `1-50`，范围查询与搜索默认 20。
- 时间支持 ISO `2026-08-29T09:00:00` 与 `2026-08-29 09:00:00` 两种格式；
  `startTime > endTime` 拒绝，错误信息给出正确格式示例。
- 可选文本参数空白视为未传，避免拼出 `data_type = ''`。
- 无用户身份（MCP 1.0），搜索保持全量只读；Day 82 传输鉴权落地前不开放公网。

## 7. 关键文件

- [McpDeviceTools.java](../../backend/src/main/java/dev/reboot/mcp/McpDeviceTools.java) — 7 个只读 @Tool
- [McpToolConfig.java](../../backend/src/main/java/dev/reboot/mcp/McpToolConfig.java) — 显式注册边界
- [ADR 0027](../decision-log/0027-mcp-tool-exposure.md) — 暴露边界决策表
- [ADR 0028](../decision-log/0028-mcp-data-tools.md) — 数据查询工具契约
