# Decision 0029: MCP 客户端集成 + 传输层令牌鉴权（Week 12）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-30 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 82 / ADR 0027 / ADR 0028 / McpDeviceTools / MCP Java SDK 0.10.0 |

## 1. 背景

Day 80-81 已建成 MCP Server（ADR 0027 / ADR 0028）：SSE 端点 `/mcp/sse` +
`/mcp/message`，显式暴露 7 个只读设备/数据工具。但 MCP 1.0 规范未定义 HTTP 鉴权头，
Spring AI 传输也不透传 Header 到工具上下文，因此该通道目前只能视为内网可信边界。

Day 82 路线图要求：SSE 连接验证 + 工具清单冒烟，并补传输层鉴权/RBAC 设计，为
Day 83 Agent + MCP 联调打通可验证、可控的连接链路。

## 2. 决策

### 2.1 引入 MCP Java SDK 客户端依赖

`pom.xml` 显式新增 `io.modelcontextprotocol.sdk:mcp:0.10.0`。该版本与 Spring AI
`spring-ai-starter-mcp-server-webmvc:1.0.3` 传递引入的 `mcp-spring-webmvc:0.10.0`
完全一致，不产生双版本；显式声明是为了直接使用 SDK 的 `McpClient` 同步 API 与
`HttpClientSseClientTransport`。

### 2.2 冒烟链路：initialize → listTools → 只读探针

新增 `McpClientService.smoke()`：

1. 用 `HttpClientSseClientTransport` 连接 `app.mcp.client.base-url` + `sse-endpoint`
   （默认本服务 `http://localhost:8080/mcp/sse`）。
2. `McpClient.sync(...)` 建立会话，`initialize()` 完成协议握手，读取 Server
   name / version / instructions。
3. `listTools()` 取工具清单（预期 7 个只读工具）。
4. 调用 `mcp_list_devices(limit=1)` 做真实链路探针，失败统一转
   `BusinessException(SERVICE_UNAVAILABLE)`。

结果经 `McpSmokeResult` record 返回，包含 Server 信息、工具清单、探针工具名与原始
JSON 文本。对外暴露 `POST /api/mcp/smoke`，`@RequireRole(ADMIN)`——冒烟属运维诊断，
只对 ADMIN 开放，走既有 JWT + RBAC 分层。

### 2.3 传输层可选共享令牌

`McpAccessFilter` 只保护 `/mcp/sse` 与 `/mcp/message` 两个 MCP 端点（Filter 注册
order 0，早于 JWT Filter 但两者路径不重叠）：

- 配置 `app.mcp.access-token`（来源 `MCP_ACCESS_TOKEN`）非空时，请求必须携带
  `X-MCP-Token` 且与配置值一致，否则返回 401 JSON。
- 令牌比较使用 `MessageDigest.isEqual`（常量时间比较），避免时序侧信道。
- 本地开发默认留空 = 内网可信直连，不启用令牌门，避免 `.env` 配置负担。
- 客户端侧在 `customizeRequest` 中注入同一 `X-MCP-Token`，测试可验证整条鉴权链路。

### 2.4 RBAC 边界

- MCP 工具保持只读且**无用户身份**，不做站点级授权（ADR 0027 既定语义）。
- 管理/写操作继续走 JWT REST 接口，MCP 通道不获得写能力。
- 需要按用户隔离时，等 MCP 1.1 OAuth / 身份头协议在 Spring AI 落地后再演进，
  不在当前版本手工造一套鉴权协议。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| 不设传输鉴权，维持内网可信 | MCP 是跨信任域协议，即使内网也应有一个显式边界，便于暴露到受控外部客户端 |
| 在 MCP 工具上下文内做 RBAC | MCP 1.0 不透传身份头，SDK 0.10 无标准方案；强行传参会让工具契约膨胀且不可信 |
| 为 `/api/mcp/smoke` 之外的 MCP 工具提供写能力 | 与只读设备查询定位冲突；写操作已有 JWT + @OperationLog 审计闭环 |

## 4. 影响与验证

- 代码：新增 `McpClientService` / `McpController` / `McpAccessFilter` /
  `McpSmokeResult`；`WebMvcConfig` 注册 MCP Filter。
- 测试：`McpAccessFilterTest` 4 用例 + `McpClientSmokeTest` 2 用例（RANDOM_PORT
  全上下文真连 SSE）+ `McpServerContextTest` 工具数断言更新为 7。
- 回归：全量后端 `Tests run: 257, Failures: 0, Errors: 0, Skipped: 0`
  （Day81 基线 251 + 新增 6）；前端 `vite build` 0 errors。
- 审计/模板（Day 83，DG-001/002）：`.env.example` 补齐 `MCP_ACCESS_TOKEN` /
  `MCP_CLIENT_BASE_URL` / `MCP_CLIENT_SSE_ENDPOINT` 模板；`POST /api/mcp/smoke`
  增加 `@OperationLog(operationType=MCP_SMOKE, targetType=MCP)`，
  Flyway V12 扩展 operation_log CHECK 约束。
- 文档：`docs/ai/mcp-learning-notes.md`、AGENTS/DAILY_ROADMAP/
  Application-Architecture 同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| 共享令牌是静态密钥 | 仅部署层下发 `MCP_ACCESS_TOKEN`，不入库、不提交；公网必须启用 |
| 冒烟测试不可达 Server 需等待超时 | `requestTimeout` / `initializationTimeout` 10s，单用例约 10s 属预期 |
| MCP 工具无用户身份，全量只读 | 只读且显式注册（ADR 0027），写操作仍走 JWT REST |
| 未来协议演进 | MCP 1.1 OAuth 就绪后按协议标准迁移，替换 Filter 即可 |

---

> 最后更新：2026-08-30 | 维护者：AI 助手 + hula0710
