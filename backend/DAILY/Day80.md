# Day 80 — MCP 协议概念 + MCP Server 开发

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 12（Agent + MCP）
> **分支**：`feat/agent-mcp`
> **配套 ADR**：[0027-mcp-tool-exposure.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0027-mcp-tool-exposure.md)
> **验收结果**：✅ **GO**（MCP Server SSE 端点落地 + 只读设备工具暴露，后端 243 tests 0 failures）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 依赖 | `backend/pom.xml` | 新增 `spring-ai-starter-mcp-server-webmvc:1.0.3` |
| 配置 | `application.yml` | `spring.ai.mcp.server` 启用：name/version/SSE 端点/capabilities |
| 工具 | `mcp/McpDeviceTools.java` | 4 个 `@Tool` 只读设备查询工具 |
| 边界 | `mcp/McpToolConfig.java` | 显式 `ToolCallbackProvider`，仅注册 MCP 工具 |
| 测试 | `mcp/McpDeviceToolsTest.java` | 8 个用例：limit/默认上限/未知设备/缺 ID |
| 测试 | `mcp/McpServerContextTest.java` | 2 个用例：McpSyncServer 自动配置 + 4 工具注册 |
| 文档 | `docs/decision-log/0027-mcp-tool-exposure.md` | ADR：工具暴露边界 |
| 文档 | `docs/ai/mcp-learning-notes.md` | MCP 协议学习笔记 |

## 二、MCP 协议概念

MCP（Model Context Protocol）是一个让 LLM 客户端通过统一协议访问外部工具/资源的标准。本项目把它当作“让外部 AI 客户端调用项目只读设备查询接口”的桥梁：

```text
MCP Client (Codex / Claude Desktop) ──SSE──> MCP Server (本项目 :8080/mcp) ──> DB
```

- **Server**：本项目，基于 Spring AI `spring-ai-starter-mcp-server-webmvc`。
- **Transport**：SSE（HTTP + Server-Sent Events），与 WebMVC 兼容，无需独立进程。
- **Capability**：仅开放 `tools`（resources/prompts/completion 全部关闭）。

## 三、SSE 端点配置

`application.yml` 关键片段：

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: industrial-ai-hub-mcp
        version: 1.0.0
        type: sync
        stdio: false
        sse-endpoint: /mcp/sse
        sse-message-endpoint: /mcp/message
        capabilities:
          tool: true
          resource: false
          prompt: false
          completion: false
        instructions: >-
          Industrial AI Hub 只读设备查询服务。工具返回 JSON；
          当前仅对内部可信 AI 客户端开放，请勿将端点暴露到公网。
```

同时在 `spring.autoconfigure.exclude` 排除 `McpWebFluxServerAutoConfiguration`，避免 WebFlux 与 WebMVC 双路由注册。

## 四、工具暴露边界

暴露的 4 个只读工具（均返回 JSON）：

| `@Tool` name | 说明 | 参数 |
| --- | --- | --- |
| `mcp_list_devices` | 列出设备摘要 | `limit`（1-50，默认 20） |
| `mcp_get_device_basic` | 单台设备基础信息 | `deviceId` |
| `mcp_list_device_recent_data` | 设备最近运行数据 | `deviceId, limit`（默认 10） |
| `mcp_list_device_recent_alarms` | 设备最近告警 | `deviceId, limit`（默认 5） |

`McpToolConfig` 通过 `MethodToolCallbackProvider.builder().toolObjects(mcpDeviceTools)` 显式注册，
**不**把内部 Agent 工具（`DeviceAiTools`，依赖 `ToolContext.userId` 做站点授权）暴露给无法携带用户身份的 MCP 客户端。

授权边界：MCP Server 视为内部可信通道（内网/本机），当前工具无站点作用域断言；
Day 82 MCP 客户端集成时再引入传输层鉴权与 RBAC。

## 五、测试

```
McpDeviceToolsTest     8/8
  ├ listDevices_shouldReturnDeviceJsonAndApplyLimit
  ├ listDevices_defaultLimitShouldCapToTwenty
  ├ getDeviceBasic_shouldReturnDeviceJson
  ├ getDeviceBasic_unknownDevice_shouldReturnErrorJson
  ├ getDeviceBasic_missingId_shouldReturnErrorJson
  ├ listDeviceRecentData_shouldReturnDataJson
  ├ listDeviceRecentData_unknownDevice_shouldNotQueryData
  └ listDeviceRecentAlarms_shouldReturnAlarmJson
McpServerContextTest    2/2
  ├ mcpServerAutoConfigurationShouldCreateSyncServer
  └ mcpDeviceToolsShouldBeRegisteredAsToolCallbacks
```

全量后端回归：`Tests run: 243, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

> 测试数说明：相较 Day79 基线 233 tests，本日新增 MCP 工具测试 8 + 上下文验证 2，净 +10。

## 六、明日计划（Day 81）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | 开发 Device MCP Server：暴露设备查询、数据查询工具（Roadmap Day 81） |
| ★★☆ | MCP 客户端集成准备：SSE 连接验证、工具清单 |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day81 日志 |
