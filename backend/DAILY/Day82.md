# Day 82 — MCP 客户端集成 + 传输层令牌鉴权

> **日期**：2026-08-30
> **阶段**：Phase 4 AI 集成 · Week 12（Agent + MCP）
> **分支**：`feat/agent-mcp`
> **配套 ADR**：[0029-mcp-client-auth-smoke.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0029-mcp-client-auth-smoke.md)
> **验收结果**：✅ **GO**（MCP 客户端冒烟链路全绿，后端 257 tests 0 failures + 前端 build 0 errors）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 依赖 | `pom.xml` | 显式新增 `io.modelcontextprotocol.sdk:mcp:0.10.0`（与 Spring AI 1.0.3 传递版本一致，ADR 0029） |
| 客户端 | `mcp/McpClientService.java` | `HttpClientSseClientTransport` 连接 SSE：initialize 握手 → listTools 工具清单 → `mcp_list_devices(limit=1)` 只读探针 |
| 入口 | `mcp/McpController.java` | `POST /api/mcp/smoke`，`@RequireRole(ADMIN)`，返回 `McpSmokeResult` |
| 鉴权 | `mcp/McpAccessFilter.java` | 可选 `X-MCP-Token` 传输层令牌门，`MessageDigest.isEqual` 常量时间比较，401 JSON |
| 配置 | `WebMvcConfig` / `application*.yml` | Filter 仅注册 `/mcp/sse`、`/mcp/message`（order 0）；`app.mcp.*` 配置默认本机直连 |
| 测试 | `McpAccessFilterTest` / `McpClientSmokeTest` / `McpServerContextTest` | 4 + 2 + 2 用例：令牌门、RANDOM_PORT 真连 SSE、7 工具注册断言 |
| ADR | `docs/decision-log/0029-mcp-client-auth-smoke.md` | 客户端接入、传输鉴权与 RBAC 边界决策 |

## 二、传输层鉴权 / RBAC 设计（ADR 0029）

MCP 1.0 规范未定义 HTTP 鉴权头，Spring AI 也不透传 Header 到工具上下文。Day 82 的边界设计：

- **传输层**：`app.mcp.access-token`（`MCP_ACCESS_TOKEN`）非空时，`/mcp/sse` 与
  `/mcp/message` 必须携带一致的 `X-MCP-Token`，否则 401；本地开发默认留空放行。
- **管理入口**：`POST /api/mcp/smoke` 走标准 JWT + ADMIN 权限，冒烟属运维诊断。
- **工具能力**：MCP 工具保持只读且无用户身份，不做站点级授权；写操作仍走 JWT REST，
  不向 MCP 通道开放。
- **演进**：MCP 1.1 OAuth / 身份头协议就绪后，用标准协议替换自定义 Filter。

## 三、测试与回归

```
McpAccessFilterTest      4/4
  ├ tokenNotConfigured_shouldPassThrough
  ├ matchingToken_shouldPassThrough
  ├ missingToken_shouldRejectWith401
  └ wrongToken_shouldRejectWith401
McpClientSmokeTest       2/2
  ├ smoke_shouldConnectAndListAllSevenTools   （RANDOM_PORT 真连 SSE 全链路）
  └ smoke_unreachableServer_shouldFailWithBusinessException
McpServerContextTest     2/2（工具注册断言 4 → 7）
```

全量后端回归：`Tests run: 257, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
前端门禁：`vite build` 748ms，0 errors。

> 测试数说明：Day81 基线 251 tests + Day82 新增 6（Filter 4 + Smoke 2）= 257。
> 不可达 Server 用例依赖 10s 连接超时，属预期慢用例。

## 四、明日计划（Day 83）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | Agent + MCP 联调：让 Agent 通过 MCP 客户端工具完成设备巡检并生成日报 |
| ★★☆ | 前端巡检结果/日报展示入口（视联调产出定） |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day83 日志 |
