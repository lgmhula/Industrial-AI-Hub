# Week 12 MCP 学习笔记：协议、传输与工具暴露边界

> 日期：2026-08-30 | 覆盖：Day 80-82（ADR 0027 / ADR 0028 / ADR 0029）

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
授权上当前视为内网可信通道；Day 82 已补 `X-MCP-Token` 传输鉴权（见 §7，ADR 0029），
RBAC 仍不进入工具上下文。

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
- 无用户身份（MCP 1.0），搜索保持全量只读；Day 82 已落地传输鉴权（§7），
  公网开放前必须配置 `MCP_ACCESS_TOKEN`。

## 7. Day 82：客户端集成与传输鉴权

MCP Server 建好后，还需要一个进程内客户端来验证链路（Day 82，ADR 0029）。
项目直接引入 MCP Java SDK `io.modelcontextprotocol.sdk:mcp:0.10.0`（与 Spring AI
1.0.3 传递版本一致），用同步 API 三步冒烟：

```text
McpClientService.smoke()
  ├ 1. HttpClientSseClientTransport 连接 /mcp/sse
  ├ 2. initialize()   → 协议握手，读 Server name / version / instructions
  ├ 3. listTools()    → 工具清单（断言 7 个只读工具）
  └ 4. callTool(mcp_list_devices, limit=1) → 真实只读探针
```

管理入口 `POST /api/mcp/smoke` 挂 `@RequireRole(ADMIN)`，结果经 `McpSmokeResult`
返回。失败统一转 `BusinessException(SERVICE_UNAVAILABLE)`。

### 7.1 传输层鉴权：X-MCP-Token

MCP 1.0 未定义 HTTP 鉴权，Spring AI 不透传 Header 到工具上下文。ADR 0029 采用
**可选的共享令牌门**：

- 配置 `app.mcp.access-token`（`MCP_ACCESS_TOKEN`）非空时，`/mcp/sse` 与
  `/mcp/message` 必须携带一致的 `X-MCP-Token`，否则 401 JSON。
- 比较用 `MessageDigest.isEqual`（常量时间），客户端在 `customizeRequest` 注入同一头。
- 本地开发默认空 = 内网可信直连，不加配置负担；公网/跨机房必须启用。

### 7.2 RBAC 边界

- MCP 工具只读、无用户身份，不做站点级授权（沿用 ADR 0027 语义）。
- 写/管理操作继续走 JWT REST；MCP 通道不获得写能力。
- MCP 1.1 OAuth / 身份头协议就绪后，用标准协议替换自定义 Filter，不手造协议。

## 8. 关键文件

- [McpDeviceTools.java](../../backend/src/main/java/dev/reboot/mcp/McpDeviceTools.java) — 7 个只读 @Tool
- [McpToolConfig.java](../../backend/src/main/java/dev/reboot/mcp/McpToolConfig.java) — 显式注册边界
- [McpClientService.java](../../backend/src/main/java/dev/reboot/mcp/McpClientService.java) — SSE 握手 + 工具清单 + 只读探针
- [McpAccessFilter.java](../../backend/src/main/java/dev/reboot/mcp/McpAccessFilter.java) — X-MCP-Token 传输鉴权门
- [McpController.java](../../backend/src/main/java/dev/reboot/mcp/McpController.java) — POST /api/mcp/smoke（ADMIN）
- [ADR 0027](../decision-log/0027-mcp-tool-exposure.md) — 暴露边界决策表
- [ADR 0028](../decision-log/0028-mcp-data-tools.md) — 数据查询工具契约
- [ADR 0029](../decision-log/0029-mcp-client-auth-smoke.md) — 客户端集成 + 传输鉴权
