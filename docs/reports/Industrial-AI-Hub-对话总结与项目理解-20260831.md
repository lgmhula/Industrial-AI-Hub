# Industrial AI Hub — 项目理解与对话总结

> **生成日期**：2026-08-31
> **生成方**：TRAE AI 助手（GLM-5.2）
> **覆盖范围**：项目全局理解 + 近期对话脉络（Day 76-84 验证 → Day 84 复盘 → 外部 IM 推送缺口识别）
> **配套总计划**：`backend/DAILY_ROADMAP.md`（16 周/112 天路线图）
> **AI 入口**：`AGENTS.md`（行为约束 + 当前状态 SSOT）
> **存放说明**：原计划放入桌面 `MD文件/` 目录，受工作目录写入限制改放此处，可手动 `cp` 到桌面

---

## 一、项目核心理解

### 1.1 项目定位

**Industrial AI Hub** — 工业 AI 设备管理平台。

- **目标**：从 Java 基础到 AI 集成的完整技术栈闭环（16 周路线图）。
- **后端**：Spring Boot 3.5 + MyBatis + MySQL。
- **前端**：Vue 3（Element Plus 暗色工业风）。
- **基础设施**：Docker Compose 统一管理（13 服务，含 backend）。
- **阶段**：Phase 4 AI 集成进行中（Day 84 已收官 Agent + MCP 模块）。

### 1.2 技术栈锁定（防漂移，AGENTS §5）

| 维度 | 选型 | 关键约束 |
|---|---|---|
| 运行时 | JDK 25 LTS（Temurin） | 唯一运行时，不降级 |
| 构建 | Maven 3.9.6（Wrapper 锁定） | 不使用 4.x RC |
| 框架 | Spring Boot 3.5.0 + MyBatis 3.0.5 | parent POM 锁定 |
| 数据库 | MySQL 8.4（Docker） | compose.yml 锁定，端口 3307 |
| 缓存 | Redis Stack 7.4.0-v1 + Redisson 3.39.0 | 三客户端并存（TD-019 待收敛） |
| MQ | RabbitMQ 4.0-management | 工作队列/发布订阅/DLQ/延迟队列 |
| 搜索 | Elasticsearch 8.17.0（预留，Day 101 ELK） | 当前不启动 |
| AI | DeepSeek API（deepseek-chat / deepseek-reasoner） | OpenAI 兼容，opt-in（ADR 0021） |
| AI 框架 | Spring AI 1.0.3（ChatClient/PromptTemplate/@Tool） | 显式版本锁定 |
| MCP | io.modelcontextprotocol.sdk:mcp:0.10.0 | SSE 传输 + HttpClientSseClientTransport |
| 认证 | JJWT 0.12.6 + BCrypt | JWT，Day 23 |
| 文档 | Knife4j 4.5.0（ADR 0013） | API 文档 |

### 1.3 分层架构（AGENTS §4.2 / §6）

```
Controller  →  Service  →  Mapper
     ↓           ↓           ↓
  ApiResponse  业务逻辑    MyBatis
     ↓           ↓           ↓
  @RequireRole  @OperationLog  Flyway 迁移
  AuthInterceptor  AOP 切面  V###__*.sql
  RateLimitInterceptor  BusinessException
```

**关键包结构**（`dev/reboot/`）：
- `controller/` — REST 控制器
- `service/` — 业务逻辑
- `client/` — DeepSeek HTTP 客户端（Phase 4）
- `mcp/` — MCP Server 工具 + 显式注册（Phase 4）
- `agent/` — ToolCallingAgent 通用循环 + AgentRunResult（Phase 4，Day 79）
- `tool/` — @Tool 声明式工具（Function Calling，Day 68）
- `mapper/` / `entity/` / `dto/` — 数据层 + ApiResponse
- `config/` — 配置类
- `security/` — JWT Filter + Auth/RateLimit 拦截器
- `aop/` — 操作日志切面
- `annotation/` — @RequireRole / @OperationLog
- `enums/` / `exception/` / `util/`

### 1.4 关键约定（AGENTS §7）

- **数据库**：`reboot`，9 张表（user/role/user_role/device/device_data/alarm/operation_log + site/user_site）。
- **Schema 管理**：Flyway（`backend/src/main/resources/db/migration/`，ADR 0019），变更 = 新增 `V###__*.sql`，当前到 V13。
- **种子数据隔离**：演示/测试数据已与生产迁移链隔离（`db/seed/dev/seed_demo_data.sql` + `scripts/seed-dev.sh` 显式执行，ADR 0019 §5）。
- **API 前缀**：`/api/`。
- **端口**：Spring Boot 8080，MySQL 3307，Redis 6379。
- **响应格式**：所有 API 返回 `ApiResponse<T>`（`dev.reboot.dto.ApiResponse`）。
- **权限模型**：RBAC（ADMIN/OPERATOR/VIEWER）+ **站点资源作用域**（P1-01，ADR 0020）—— 设备/告警/数据按 `user_site` 站点内角色授权，全局 ADMIN 隐式全站点。
- **逻辑删除**：device 表 `is_deleted` + `softDeleteById`。
- **AI 能力**：DeepSeek 默认关闭（`DEEPSEEK_ENABLED=false`），启用需在 `.env` 配置 `DEEPSEEK_API_KEY`；未启用/缺 Key 时 `/api/ai/*` 返回 503。

### 1.5 密钥 SSOT（AGENTS §8 / ADR 0015）

- **dev**：根目录 `.env` 是唯一事实源，`application-dev.yml` 经 `spring.config.import` 自动读取。
- **test**：`application-test.yml` 隔离占位值，不读取本地 `.env`。
- **prod**：容器环境变量，`compose.yml` 经 `${VAR}` 插值注入。
- **强制规则**：禁止真实密钥入 Git；禁止 IDE 配置手填密钥；禁止 `application*.yml` 硬编码；敏感变量禁止 `${VAR:}` 空默认，缺失必须 fail-fast。

### 1.6 Git 工作流纪律（ADR 0017，违反即视为缺陷）

- `main` = 唯一发布线，**禁止直推**（含 AI 会话）。
- 日常开发走分支：`feat/*`、`fix/*`、`docs/*`、`chore/*`、`ai/*`。
- 合并流程：分支自测 → push → GitHub PR 自审 → 合并（squash / --no-ff）→ 删除分支。
- 发布：达到验收态 → 打 `v2.x.y` tag + 审计报告 → 副本/部署锁定该 tag。
- 例外：治理类元变更（AGENTS.md / ADR 自身）可直推 main，须标注 `(governance bootstrap)`。

---

## 二、当前进度（截至 Day 84）

### 2.1 基线

- **当前基线**：v2.3.0（Tag: `v2.3.0`，PR #13 squash 合并成功，Release Gate: GO）。
- **上一基线**：v2.2.0（Commit: `892c4a5`，ADR 0015 密钥 SSOT / ADR 0016 charset-safe init）。
- **测试基线**：Day 83 收官 269 tests 0 failures（Day 75 基线 227 → Day 79 233 → Day 83 269）。

### 2.2 已完成模块（按时间线）

**Phase 1-3（Day 1-65，已收官）**：
- Java 复苏 → SpringBoot → MyBatis → CRUD → 中间件武装（Redis + RabbitMQ + Docker + Linux 部署）。
- 89/89 测试全绿，第三阶段检查点达成。
- Day 65 合并验收：后端 195 tests 0 failures；前端 build 875ms 0 errors；浏览器 6 页面 0 控制台错误；三角色 API 冒烟 48/48 全绿。

**Phase 4 AI 集成（Day 66-84，进行中）**：
- **Day 66 DeepSeek AI**：`/api/ai/chat` 文本补全 + 告警摘要 + 设备健康诊断，token 用量 + 结构化 JSON，ADR 0021。
- **Day 67 Spring AI**：ChatClient/PromptTemplate 抽象 + 告警列表 AI 摘要 Dialog + 设备详情 AI 健康诊断卡片 + Flyway V9 AI 操作日志，ADR 0022 / TD-028。
- **Day 68 Function Calling**：@Tool 声明式三工具 + 3 轮硬限 Agent + `POST /api/ai/agents/device-status` + 设备详情问答折叠面板 + Flyway V10 FUNCTION_CALL 审计，ADR 0023。
- **Day 69 前端视觉升级**：DESIGN.md 深色工业控制中心 + Element Plus 暗色覆盖 + ECharts 按需注册。
- **Day 70 复盘/笔记**：Week10 周复盘 + `docs/ai/phase4-ai-learning-notes.md`。
- **Day 71/72 RAG 入库**：Qdrant 选型 ADR 0024 + TextChunker + LocalHashEmbeddingModel + SimpleVectorStore + RagIngestionService。
- **Day 73 RAG 检索**：RagRetrievalService + KnowledgeChunk，Top-K 余弦相似度检索。
- **Day 74 RAG PDF 导入**：PDFBox 3.0.8 + RagController 上传 + Flyway V11 INGEST/KNOWLEDGE 审计。
- **Day 75 RAG 运维助手**：AiService.answerWithRag + `POST /api/rag/ask`。
- **Day 76 RAG 前端**：RagAssistant 对话页 + `/assistant` 路由 + Sidebar AI 助手入口。
- **Day 77 复盘/笔记**：Week11 周复盘 + `docs/ai/rag-learning-notes.md`。
- **Day 78 Agent 概念**：ReAct 手动循环 + 3 轮硬限 + 可观测性，ADR 0026。
- **Day 79 多步 Agent**：ToolCallingAgent 通用循环 + `list_device_recent_data` 工具 + DeviceAnalysisAgentService + `/api/ai/agents/device-analysis`。
- **Day 80 MCP Server**：spring-ai-starter-mcp-server-webmvc + SSE 端点 + McpDeviceTools 4 只读工具 + McpToolConfig 显式暴露边界，ADR 0027。
- **Day 81 MCP 数据工具**：`mcp_get_device_data_range` / `mcp_get_device_data_stats` / `mcp_search_devices`，时间范围 + 聚合统计 + 动态搜索，ADR 0028。
- **Day 82 MCP 客户端**：MCP Java SDK 0.10.0 + McpClientService SSE 握手/工具清单/只读探针 + `POST /api/mcp/smoke`（ADMIN）+ McpAccessFilter `X-MCP-Token` 传输鉴权，ADR 0029。
- **Day 83 缺口补齐 + Agent+MCP 联调**：`.env.example` 补 MCP 3 变量模板（DG-001）；`POST /api/mcp/smoke` 增 `@OperationLog(MCP_SMOKE/MCP)` + Flyway V12（DG-002）；McpInspectionAgentService 单会话巡检 + McpToolCallbackAdapter + `POST /api/ai/agents/inspection-report`（ADMIN，INSPECTION/MCP 审计）+ Flyway V13，ADR 0030。
- **Day 84 复盘/笔记**：Week12 周复盘（7 段式结构）+ `docs/ai/agent-learning-notes.md` 扩展到 Day 83 + `docs/ai/mcp-learning-notes.md` 扩展到 Day 83。Agent+MCP 模块收官。

### 2.3 下一步（Day 85，AGENTS §3）

- ★★★ AI 生成日报经 RabbitMQ 自动推送到前端。
- ★★☆ 巡检日报前端展示页（接 WebSocket/SSE 推送）。
- ★☆☆ 同步 AGENTS/ROADMAP 与 Day85 日志。

### 2.4 待实现（远期）

- ELK 日志（Day 101，可选）。
- Phase 4 AI 集成剩余项：RAG 真实向量库/Embedding 替换、Agent 日报展示/消息推送。
- **Phase 5 PLC + MQTT**（Day 92-112）：PLC 模拟、MQTT、完整部署上线。

---

## 三、对话总结（近期脉络）

### 3.1 阶段一：Day 76-83 验证（codex 完成度校验）

用户多次请求验证 codex 完成的代码：
- **Day 76-77**：RAG 前端 + Week11 复盘校验。
- **Day 78-79**：Agent 概念 + 多步 Agent 验证。
- **Day 80-81**：MCP Server + MCP 数据工具验证。
- **Day 82-83**：MCP 客户端 + Agent+MCP 联调验证。

**关键校验点**：
- ADR 0026-0030 决策闭环。
- Flyway 迁移链 V9-V13 一致性。
- `@OperationLog` 覆盖所有 AI 端点（CHAT/SUMMARY/DIAGNOSE/FUNCTION_CALL/MCP_SMOKE/INSPECTION）。
- MCP 工具只读 + `X-MCP-Token` 传输鉴权 + `limit` 1-50 clamp。
- ToolCallingAgent 3 轮硬限（巡检 6 轮）+ `forceFinalize()` 截断。
- McpInspectionSession `AutoCloseable` 防连接泄漏。
- 269 tests 0 failures。

### 3.2 阶段二：DeepSeek API Key 真实接入

用户决策接入真实 DeepSeek API（"都是真金白银"）：
- 引导用户在 `.env` 安全配置（不入 Git）。
- 最小 token 用量验证（短 prompt）。
- 测试后清理（避免持续消耗）。
- ADR 0021 opt-in 机制完善（`DEEPSEEK_ENABLED=true` + `DEEPSEEK_API_KEY` 双开关）。
- 启动期 WARN 日志（TD-024，待补：`enabled=true && apiKey.isBlank()`）。

### 3.3 阶段三：Day 84 复盘（AI 助手接管，比 codex 更细致）

用户："你来 day 84；我希望你比 codex 更细致"。

**交付物**（分支 `docs/week12-review`，无代码变更）：
- 新建 `backend/REVIEW/Week12.md`：7 段式结构（目标 vs 实际、关键洞察、演进、检查点、技术债、改进、展望），对齐 Week11 结构。
- 新建 `backend/DAILY/Day84.md`：4 段（产出、Week12 收官要点、文档质量检查、Day85 计划）。
- 更新 `docs/ai/agent-learning-notes.md`：扩展到 Day 83，新增 §7 泛化（4 设计点）、§8 集成（单会话/适配器/复用/授权）、§9 关键文件清单。
- 更新 `docs/ai/mcp-learning-notes.md`：扩展到 Day 83，新增 §8 集成（单 SSE 会话/McpToolCallbackAdapter/Agent 复用/授权/边界）、§9 关键文件清单。
- 同步 `AGENTS.md §3`（下一步 Day85、已完成模块增 Day84）+ `DAILY_ROADMAP`（Day84 标记完成）。

**优于 codex 的关键点**：
1. 严格 7 段式结构对齐 Week11。
2. 真实行号引用（file:/// + #L 锚点）。
3. 测试演进追踪（227→233→269）。
4. 端点对照表（路径/方法/角色/审计）。
5. 主动登记技术债。
6. 笔记同步更新（不只新建）。

### 3.4 阶段四：外部 IM 推送缺口识别（当前议题）

用户："目前为止是不是缺少了什么；比如之前我们谈到真实的 api 模型密钥访问（目前已填写加入）；接下来日报消息这些是不是没设定推送到微信或者叮叮这些？？"

**核实结果**：
- ✅ DeepSeek API Key 已配置（`.env.example` 模板 + `.env` 真实密钥）。
- ❌ 外部 IM 推送（钉钉/微信）确实未实现：代码库无 `webhook`/`dingtalk`/`wechat` 命中；Day 83 巡检日报只通过 REST 端点同步返回。

**缺口分析**：
Day 85 计划的「RabbitMQ 推送」是**内部消息队列**（后端 → 前端 WebSocket/SSE），**不覆盖**外部 IM 推送场景。对工业 AI 平台而言这是真实缺失：

| 场景 | 现状 | 工业需求 |
|---|---|---|
| 巡检日报 | ADMIN 主动调 API 拉取 | 每日定时生成 → 推钉钉群 |
| 告警通知 | DB 落库 + 前端轮询 | 严重告警实时推钉钉/企业微信 |
| 异常风险 | 嵌入日报文本 | 触发阈值即推 IM |

**钉钉集成方案**（基于 2026-07 最新 API 规范）：
1. **接口规范**：Webhook URL `https://oapi.dingtalk.com/robot/send?access_token=XXX&timestamp=TS&sign=SIGN`；HmacSHA256 签名（`timestamp\n` + secret）→ Base64 → URLEncode；限流 20 条/分钟。
2. **消息类型**：巡检日报 → Markdown；告警通知 → ActionCard（带"查看详情"按钮跳转前端）；系统告警 → text @ 责任人（@mobiles）。
3. **集成点**：`AlarmRuleEngine` 触发告警 → `AlarmPushConsumer` → `DingTalkClient.pushAlert`；`McpInspectionAgentService.generate()` → `DingTalkClient.pushDailyReport`（定时任务）。
4. **配置项**（`.env`）：`DINGTALK_ENABLED` / `DINGTALK_WEBHOOK_URL` / `DINGTALK_SECRET` / `DINGTALK_KEYWORD`。
5. **不引入新依赖**：复用 Spring `RestClient`（Spring Boot 3.5 内置）+ 现有 `Guava RateLimiter`。

**待用户决策**：
- **A**：起草 ADR 0031（钉钉机器人集成决策）+ 详细技术方案文档。
- **B**：直接进入实现（Day 85.5：DingTalkClient + 告警/日报推送 + 单测）。
- **C**：先做 Day 85（RabbitMQ→前端），钉钉集成作为 Day 86 独立任务。

---

## 四、关键硬约束（AI 助手必须遵守）

### 4.1 AI 相关（项目记忆）

- 所有 AI 操作必须 `@OperationLog`，targetType='AI'，operationType ∈ {CHAT/SUMMARY/DIAGNOSE/FUNCTION_CALL/MCP_SMOKE/INSPECTION}。
- DeepSeek 必须 opt-in（`DEEPSEEK_ENABLED=true`），默认关闭。
- 第三方 AI 不可用必须返回 HTTP 503，message 含「未启用/未配置/第三方」。
- Spring AI 依赖必须显式版本锁定（如 `spring-ai-starter-model-openai:1.0.3`）。
- AI 响应 JSON 解析失败必须降级为纯文本 + WARN 日志。
- Agent 工具执行必须手动驱动，3 轮硬限（巡检 6 轮）+ `forceFinalize()` 截断。
- AI 请求/结果 DTO 必须含特定校验注解和字段（`@NotNull`/`@NotBlank`/`@Size(2000)`；answer/toolRounds/toolCalls/referencedRealTime/truncated/toolTrace）。
- 敏感环境变量禁止空默认，缺失必须 fail-fast。

### 4.2 MCP 相关

- MCP 工具必须只读（无副作用），通过 `McpToolConfig` 显式注册。
- SSE 端点（`/mcp/sse`、`/mcp/message`）由 `McpAccessFilter` + `X-MCP-Token` 鉴权。
- MCP 客户端使用 `io.modelcontextprotocol.sdk:mcp:0.10.0` + `HttpClientSseClientTransport` + 10s 超时。
- 巡检会话必须 `AutoCloseable` 防连接泄漏。
- `limit` 参数 clamp 1-50 防大 JSON 响应。

### 4.3 通用工程约定

- AI 端点在 `/api/ai` 下，`@RequireRole(VIEWER+)` 授权。
- Flyway 迁移 CHECK 约束必须覆盖 `@OperationLog` 实际使用的所有 `operation_type` / `target_type`。
- 前端 AI 组件必须实现三态：loading、error banner、content display。
- 构造器注入，不使用 `@Autowired` 字段注入。
- 包名小写、类名 PascalCase、方法/变量 camelCase。
- 4 空格缩进、UTF-8、LF 换行（`.editorconfig`）。
- 多 AI 协作卫生：禁止提交 `.qoder/`/`.workbuddy/`/`.codex/`/`.cursor/`/`.idea/`/`.vscode/`/`.DS_Store` 等产物。

### 4.4 已知陷阱（Lessons Learned）

- codex 可能漏建每日日志（Day66/67/68 曾发生），需手动补建。
- codex 可能在主功能未实现前就在 AGENTS.md 提前标记完成，需校验。
- koa-connect 包装器导致 `ctx.state` 数据丢失，必须原生 Koa 重写而非包装 Express 中间件。

---

## 五、关键文件速查表

### 5.1 治理 / 计划 / 状态

| 文件 | 路径 | 作用 |
|---|---|---|
| AGENTS.md | 项目根 | AI 入口 + 行为约束 + 当前状态 SSOT |
| DAILY_ROADMAP.md | `backend/` | 16 周/112 天总计划 + 每日任务 |
| DayXXX.md | `backend/DAILY/` | 当天产出 + 明日计划 |
| WeekXX.md | `backend/REVIEW/` | 阶段性总结（7 段式） |
| Application-Architecture.md | `docs/Architecture/` | 技术栈 + 分层 + API |
| Infrastructure-Baseline.md | `docs/Architecture/` | Docker/网络/端口规范 |
| SETUP.md | `docs/` | 克隆 → 配置 → 运行 → 验证 全流程 |
| CONTRIBUTING.md | 项目根 | 分支流程 + 自测清单 |
| TECH-DEBT.md | `docs/` | 技术债务 SSOT 清单 |
| 0001~0030 ADR | `docs/decision-log/` | 关键技术决策理由 |

### 5.2 AI 模块关键代码

| 文件 | 路径 | 作用 |
|---|---|---|
| DeepSeekClient | `dev/reboot/client/` | DeepSeek HTTP 客户端 + ensureAvailable |
| AiController | `dev/reboot/controller/` | `/api/ai/*` + `/api/ai/agents/*` 端点 |
| AiService | `dev/reboot/service/` | answerWithRag + 告警摘要 + 设备诊断 |
| ToolCallingAgent | `dev/reboot/agent/` | 通用 ReAct 循环（轮次硬限 + 可观测性） |
| AgentRunResult | `dev/reboot/agent/` | answer/toolRounds/toolCalls/toolTrace/truncated |
| DeviceAiTools | `dev/reboot/tool/` | @Tool 声明式三工具 + list_device_recent_data |
| DeviceAnalysisAgentService | `dev/reboot/service/` | 多步 Agent：查设备 → 查数据 → 分析 |
| McpInspectionAgentService | `dev/reboot/service/` | 6 轮巡检 Agent（MCP 工具） |
| McpInspectionSession | `dev/reboot/mcp/` | 单 SSE 会话管理（AutoCloseable） |
| McpToolCallbackAdapter | `dev/reboot/mcp/` | MCP 工具 → Spring AI ToolCallback 适配 |
| McpClientService | `dev/reboot/mcp/` | SSE 握手 + 工具清单 + 只读探针 |
| McpAccessFilter | `dev/reboot/mcp/` | X-MCP-Token 传输鉴权 |
| McpDeviceTools / McpDataTools / McpSearchTools | `dev/reboot/mcp/` | MCP Server 只读工具 |
| McpToolConfig | `dev/reboot/config/` | MCP 工具显式注册边界 |
| RagIngestionService / RagRetrievalService | `dev/reboot/service/` | RAG 入库/检索 |
| OperationLogAspect | `dev/reboot/aop/` | @OperationLog 切面 |

### 5.3 Flyway 迁移链

| 版本 | 内容 |
|---|---|
| V1-V3 | 初始 schema（user/role/user_role/device/device_data/alarm/operation_log） |
| V4 | site / user_site（站点作用域，ADR 0020） |
| V5-V8 | pc_hula 合并增量（Role/User 扩展、搜索/告警批量） |
| V9 | AI 操作日志（CHAT/SUMMARY/DIAGNOSE，TD-028） |
| V10 | FUNCTION_CALL 审计（Day 68） |
| V11 | INGEST/KNOWLEDGE 审计（Day 74 RAG PDF 导入） |
| V12 | MCP_SMOKE/MCP 审计（Day 83，DG-002） |
| V13 | INSPECTION/MCP 审计（Day 83，ADR 0030） |

---

## 六、待决问题（等待用户拍板）

1. **钉钉集成路径**：A（先 ADR）/ B（直接实现）/ C（Day 86 独立任务）。
2. **Day 85 是否先行**：RabbitMQ→前端推送是否优先于钉钉集成。
3. **告警推送策略**：全量推送 vs 严重告警阈值过滤（如 severity ≥ HIGH）。
4. **日报推送时机**：每日固定时间（如 09:00）vs ADMIN 触发。
5. **企业微信是否同步纳入**：方案对齐钉钉，仅 webhook URL 和签名算法不同。

---

> 本文档由 TRAE AI 助手基于对话上下文与项目记忆生成，如需更新请直接编辑或通知助手修订。
