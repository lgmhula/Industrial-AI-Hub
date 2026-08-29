# AGENTS.md — AI 执行入口

> **每次执行任务前，必须先读本文件。**
> 本文件是 AI 助手（Codex / Qoder / 其他）的唯一对齐锚点。

***

## 1. 项目简介

**Industrial AI Hub** — 工业 AI 设备管理平台。
后端 Spring Boot 3.5 + MyBatis + MySQL，前端预留（Vue3），基础设施 Docker Compose 统一管理。
目标：从 Java 基础到 AI 集成的完整技术栈闭环（16 周路线图）。

***

## 2. 文档索引（按优先级）

| 优先级 | 文档           | 路径                                                     | 作用                                                            |
| :-: | ------------ | ------------------------------------------------------ | ------------------------------------------------------------- |
| ★★★ | 本文件          | `AGENTS.md`                                            | AI 入口 + 行为约束                                                  |
| ★★★ | 每日路线图        | `backend/DAILY_ROADMAP.md`                             | 总计划 + 每日任务                                                    |
| ★★★ | 应用架构         | `docs/Architecture/Application-Architecture.md`        | 技术栈 + 分层 + API                                                |
| ★★☆ | 从零复刻指南       | `docs/SETUP.md`                                        | 克隆 → 配置 → 运行 → 验证 全流程                                         |
| ★★☆ | 基础设施基线       | `docs/Architecture/Infrastructure-Baseline.md`         | Docker/网络/端口规范                                                |
| ★★☆ | 架构决策记录       | `docs/decision-log/0001~0023`                          | 关键技术决策理由（含 0015 密钥 SSOT / 0016 charset / 0017 分支策略 / 0018 CI / 0021 DeepSeek / 0022 Spring AI / 0023 Function Calling） |
| ★★☆ | Phase 3-A 计划 | `docs/plans/phase3-a-infrastructure-stabilization.md`  | 基础设施稳定化任务分解 + 验收标准                                            |
| ★★☆ | 新设备部署手册      | `docs/reports/deploy-runbook-new-device.md`            | 全量坑位 + 一键部署指令（副本免扫描）                                          |
| ★★☆ | 参与贡献指南       | `CONTRIBUTING.md`                                      | 分支流程 + 自测清单 + 工程约束摘要                                          |
| ★☆☆ | 每日日志         | `backend/DAILY/DayXXX.md`                              | 当天产出 + 明日计划                                                   |
| ★☆☆ | 周复盘          | `backend/REVIEW/WeekXX.md`                             | 阶段性总结                                                         |
| ★☆☆ | 审计报告         | `docs/reports/Architecture-Consistency-Report-v1.2.md` | 文档-代码一致性检查                                                    |
| ★☆☆ | SQL 审计       | `docs/reports/SQL-Audit-Report.md`                     | SQL 文件一致性审计                                                   |
| ★☆☆ | 技术债务         | `docs/TECH-DEBT.md`                                    | 技术债务 SSOT 清单                                                  |
| ★☆☆ | DB Changelog | `docs/decision-log/0012-database-changelog.md`         | 数据库变更记录                                                       |
| ★☆☆ | SQL README   | `backend/src/main/resources/sql/README.md`             | SQL 初始化说明                                                     |

***

## 3. 当前状态

* **基线**：v2.3.0（Tag: `v2.3.0`，建议打在 pc\_hula 合并 commit）——PR #13 squash 合并成功；Release Gate: **GO**（见 Day 65 验收报告）

* **上一基线**：v2.2.0（Tag: `v2.2.0`，Commit: `892c4a5`）——含 ADR 0015 密钥 SSOT / ADR 0016 charset-safe init / 跨平台交接修复；Release Gate: GO

* **阶段**：Phase 3 学习路线与稳定化全部收官（Redis/RabbitMQ/Docker/Linux + Day 64 质量加固 + Day 65 pc\_hula 合并验收）；**Phase 4 AI 集成进行中**（Day 66：DeepSeek API 基础 + 告警摘要/设备诊断，ADR 0021；Day 67：Spring AI ChatClient/PromptTemplate 抽象 + 前端 AI 入口 + AI 操作日志，ADR 0022 / TD-028；Day 68：Function Calling @Tool 声明式工具 + 3 轮硬限 Agent + 设备详情问答面板，ADR 0023；Day 69：前端工业化视觉升级落地，DESIGN.md 设计系统；Day 70：Week 10 复盘 + Phase 4 AI 模块学习笔记；Day 71：RAG 概念 + 向量库选型，ADR 0024；Day 72：文档切片 + 哈希向量 + 内存向量库入库；Day 73：知识检索服务 + KnowledgeChunk 结果映射）

* **Phase 3 收官治理（P0）**：演示/测试种子数据已与生产 Flyway 迁移链隔离（`V2__seed_test_data.sql` 退役 → `db/seed/dev/seed_demo_data.sql` + `scripts/seed-dev.sh` 显式执行，幂等；全新生产库不再自动灌入 Demo 数据，见 ADR 0019 §5）

* **Git 治理**：`main` = 唯一发布线（**禁止直推**，见 §4.4 / ADR 0017）；日常开发走分支 + PR 合并；发布打 tag

* **Phase 3-A 归档**：`docs/plans/phase3-a-infrastructure-stabilization.md` + `docs/reports/archive/phase3-a-plan-audit*.md`

* **pc\_hula 分支合入内容（v2.2.0 → v2.3.0 增量）**：后端 15 新增 API 端点（RoleController 6 + UserController 扩展 7 + 搜索/告警批量）、前端 4 新页面（UserList / RoleList / Register / NotFound）、Flyway 迁移链延伸至 V8（共 8 个版本化迁移）、MySqlMigrationV7IT 新增；数据库表共 9 张（user / role / user\_role / device / device\_data / alarm / operation\_log / site / user\_site）

* **已完成**：Phase 3 中间件武装全部收官（Redis + RabbitMQ + Docker + Linux 部署，89/89 测试全绿，第三阶段检查点达成）；**Day 65 合并验收**（后端 195 tests 0 failures 3 skipped；前端 build 875ms 0 errors；浏览器 6 页面 0 控制台错误；三角色 API 冒烟 48/48 全绿）

* **下一步**：Day 74 — 设备手册知识库实战：PDF 解析 + 切片 + 入库

* **已完成模块**：设备 CRUD、JWT/BCrypt 认证、RBAC 权限 + 站点资源作用域（ADR 0020）、分页查询、全局异常处理、@Valid 校验、报警规则引擎（8 规则）、AOP 操作日志、Postman 测试集、Vue 3 前端（登录/仪表盘/设备/告警/日志/用户/角色 9 页面 + Register/NotFound）、Redis 缓存（Spring Cache + Redisson 分布式锁）、RabbitMQ 消息（工作队列/发布订阅/DLQ/延迟队列）、Docker 容器化 + Nginx 反代、Linux 部署（systemd）、**Day 66 DeepSeek AI**（/api/ai/chat 文本补全 + 告警摘要 + 设备健康诊断，token 用量 + 结构化 JSON，ADR 0021）、**Day 67 Spring AI**（ChatClient/PromptTemplate 抽象 + 告警列表 AI 摘要 Dialog + 设备详情 AI 健康诊断卡片 + Flyway V9 AI 操作日志，ADR 0022）、**Day 68 Function Calling**（@Tool 声明式三工具 + 3 轮硬限 Agent + POST /api/ai/agents/device-status + 设备详情问答折叠面板 + Flyway V10 FUNCTION_CALL 审计，ADR 0023）、**Day 69 前端视觉升级**（DESIGN.md 深色工业控制中心 + Element Plus 暗色覆盖 + 表格/AI 卡/认证页统一 + ECharts 按需注册）、**Day 70 复盘/笔记**（Week10 周复盘 + docs/ai/phase4-ai-learning-notes.md）、**Day 71/72 RAG 入库**（Qdrant 选型 ADR 0024 + TextChunker + LocalHashEmbeddingModel + SimpleVectorStore + RagIngestionService）、**Day 73 RAG 检索**（RagRetrievalService + KnowledgeChunk，Top-K 余弦相似度检索）

* **待实现**：ELK 日志（Day 101，可选）、Phase 4 AI 集成（RAG PDF 导入/AI 运维助手/Agent/MCP）、Phase 5 PLC + MQTT

***

## 4. 行为约束（必须遵守）

### 4.1 执行前

* [ ] 读本文件（AGENTS.md）

* [ ] 读 `DAILY_ROADMAP.md` 确认当前 Day 和任务

* [ ] 读最近一天的 `DAILY/DayXXX.md` 确认昨日进度和明日计划

* [ ] 如涉及架构变更，读 `Application-Architecture.md` + 相关 ADR

### 4.2 执行中

* **不做未要求的事** — 只完成当前 Day 的任务，不超前实现

* **保持文档同步** — 修改代码后，同步更新相关文档（架构文档、ADR、日志）

* **遵循分层架构** — Controller → Service → Mapper，不跳层

* **统一响应格式** — 所有 API 返回 `ApiResponse<T>`（`dev.reboot.dto.ApiResponse`）

* **代码风格** — 遵循 `.editorconfig`（4 空格缩进、UTF-8、LF 换行）

* **构造器注入** — 不使用 `@Autowired` 字段注入

* **命名规范** — 包名小写、类名 PascalCase、方法/变量 camelCase

* **多 AI 协作卫生** — 禁止提交 AI/IDE 产物（`.qoder/`/`.workbuddy/`/`.codex/`/`.cursor/`/`.idea/`/`.vscode/`/`.DS_Store` 等，已列入 `.gitignore`）；需要留档的分析/审计产物放入 `docs/reports/` 并显式提交，勿留在工作区或 `.tmp`/`.bak`

### 4.3 执行后

* [ ] 更新 `backend/DAILY/DayXXX.md`（今日产出 + 明日计划）

* [ ] 如有架构变更，更新 `Application-Architecture.md`

* [ ] 如有新决策，新增 `docs/decision-log/00XX-*.md`

* [ ] 更新本文件 §3「当前状态」

* [ ] Git commit（描述格式：`Day XXX: 简要说明`；**禁止直推 main**，见 §4.4）

### 4.4 Git 工作流纪律（ADR 0017，违反即视为缺陷）

* **main = 唯一发布线**：永远处于已验收、可部署状态；禁止任何直推（含 AI 会话）。

* **日常开发走分支**：`feat/*`、`fix/*`、`docs/*`、`chore/*`、`ai/*`（AI 会话必须先建分支）。

* **合并流程**：分支自测通过（后端构建 + `./mvnw test` + 前端 build）→ push 分支 → GitHub PR 自审 → 合并（squash / --no-ff）→ 删除分支。

* **发布**：达到验收态 → 打 `v2.x.y` tag + 审计报告 → 副本/部署锁定该 tag（不跟 main HEAD 漂移）。

* **分支卫生**：合并即删；历史分支先打 `archive/*` tag 再删除，不留死分支。

* **例外**：治理类元变更（本文件 / ADR 自身）可直推 main，提交信息须标注 `(governance bootstrap)`。

***

## 5. 技术栈锁定（防漂移）

| 组件                  | 版本               | 说明                             |
| ------------------- | ---------------- | ------------------------------ |
| JDK                 | 25 LTS (Temurin) | 唯一运行时，不降级                      |
| Maven               | 3.9.6            | 通过 Maven Wrapper 锁定，不使用 4.x RC |
| Spring Boot         | 3.5.0            | parent POM 锁定                  |
| MyBatis Spring Boot | 3.0.5            | 显式声明                           |
| MySQL Connector/J   | 9.2.0            | parent 管理                      |
| MySQL (Docker)      | 8.4              | compose.yml 锁定                 |
| Redis Stack         | 7.4.0-v1         | compose.yml 锁定                 |
| RabbitMQ            | 4.0-management   | compose.yml 锁定                 |
| Nacos               | 2.4.3            | 预留基础设施（不纳入当前路线，不启动）            |
| Elasticsearch       | 8.17.0           | compose.yml 锁定                 |
| Jedis               | 5.2.0            | Redis 客户端（Day 43）              |
| Redisson            | 3.39.0           | 分布式锁（Day 46）                   |
| Guava               | 33.4.0-jre       | RateLimiter 限流（Day 37）         |
| PageHelper          | 2.1.0            | 分页（Day 25）                     |
| Knife4j             | 4.5.0            | API 文档（ADR 0013）               |
| JJWT                | 0.12.6           | JWT 认证（Day 23）                 |
| DeepSeek API        | deepseek-chat / deepseek-reasoner | OpenAI 兼容，Phase 4 LLM（ADR 0021，可选启用） |

> **规则**：未经讨论，不得升级任何依赖版本。新增依赖必须在 ADR 中记录理由。

***

## 6. 项目结构速查

```
Industrial-AI-Hub/
├── AGENTS.md                  ← 你正在读的文件
├── compose.yml                # Docker 基础设施（13 服务，含 backend）
├── .env / .env.example        # 环境变量
├── backend/
│   ├── pom.xml                # Maven POM（Spring Boot 3.5 parent）
│   ├── .mvn/                  # Maven Wrapper + JVM 配置
│   ├── DAILY_ROADMAP.md       # 112 天路线图
│   ├── DAILY/                 # 每日日志
│   ├── REVIEW/                # 周复盘
│   ├── PROMPTS/               # 任务 prompt（可选）
│   ├── learning/              # 学习代码（Day01-54 练习，不参与构建，见 §6 附注）
│   └── src/main/java/
│       └── dev/reboot/        # 主应用代码
│           ├── controller/    # REST 控制器
│           ├── service/       # 业务逻辑
│           ├── client/        # DeepSeek HTTP 客户端（Phase 4）
│           ├── mapper/        # MyBatis Mapper
│           ├── entity/        # 实体类
│           ├── dto/           # DTO + ApiResponse
│           ├── config/        # 配置类
│           ├── security/      # JWT Filter + Auth/RateLimit 拦截器
│           ├── aop/           # 操作日志切面
│           ├── annotation/    # @RequireRole / @OperationLog
│           ├── enums/         # ErrorCode / RoleEnum
│           ├── exception/     # BusinessException + 全局异常处理
│           ├── mq/            # RabbitMQ 生产者/消费者
│           ├── rule/          # 报警规则引擎
│           └── util/          # JwtUtils 等工具类
├── docs/
│   ├── Architecture/          # 架构文档
│   ├── decision-log/          # ADR 决策记录
│   └── reports/               # 审计报告
├── mysql/                     # MySQL 配置 + 数据
├── redis/                     # Redis 配置 + 数据
└── ...                        # 其他中间件目录
```

***

## 7. 关键约定

* **数据库**：`reboot`，9 张表（user/role/user\_role/device/device\_data/alarm/operation\_log + site/user\_site）；schema 由 **Flyway** 管理（`backend/src/main/resources/db/migration/`，ADR 0019），变更 = 新增 `V###__*.sql`；**演示/测试种子数据禁止放入迁移目录**，唯一事实源 `db/seed/dev/seed_demo_data.sql`，开发环境经 `scripts/seed-dev.sh` 显式执行（幂等，见 ADR 0019 §5）；`site/user_site` 由 V4 引入（P1-01 站点作用域，ADR 0020）

* **API 前缀**：`/api/`

* **端口**：Spring Boot 8080，MySQL(Docker) 3307，Redis 6379

* **密码加密**：BCrypt（已实现，Day 23）

* **AI 能力**：DeepSeek 默认关闭（`DEEPSEEK_ENABLED=false`），启用需在 `.env` 配置 `DEEPSEEK_API_KEY`；未启用/缺 Key 时 `/api/ai/*` 返回 503（ADR 0021）

* **认证方式**：JWT — JwtUtils 生成/验证/解析 + AuthService 登录/注册（已实现，Day 23）

* **权限模型**：RBAC（拦截器 AuthInterceptor + @RequireRole 注解）+ **站点资源作用域**（P1-01，ADR 0020）——设备/告警/数据按 `user_site` 站点内角色授权，全局 ADMIN 隐式全站点（SiteAccessService）

* **逻辑删除**：device 表 is\_deleted + softDeleteById（已实现，Day 24 post-audit）

***

## 8. 密钥与敏感配置来源（SSOT，ADR 0015）

> **项目根目录** **`.env`** **是本地开发环境敏感配置的唯一事实源（Single Source of Truth）。**

### 8.1 加载机制

| 环境              | 密钥来源                         | 机制                                                                                                                    |
| --------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| dev（IDEA / 命令行） | 根目录 `.env`                   | `application-dev.yml` 经 `spring.config.import` 自动读取（双候选 `../.env` / `./.env`，兼容 backend/ 与项目根两种工作目录）；**无需手工设置任何环境变量** |
| test            | `application-test.yml` 隔离占位值 | 不读取本地 `.env`                                                                                                          |
| prod（Docker 容器） | 容器环境变量                       | `compose.yml` 经 `${VAR}` 插值注入；**不依赖本地** **`.env`**                                                                    |

### 8.2 优先级

```
dev : OS 环境变量 > .env > application.yml 默认值
docker : .env → Docker Compose → Container Environment
prod : 由部署环境注入，不依赖开发 .env
```

### 8.3 强制规则（违反即视为缺陷）

* 禁止将真实密钥提交 Git（`.env` 已入 `.gitignore`）。

* 禁止在 IDEA Run Configuration / 其他 IDE 配置中手工复制密钥（会导致漂移，见 ADR 0015 事故）。

* 禁止在 `application*.yml` 中硬编码密钥。

* `.env.example` 只保留变量名与说明，禁止真实密钥。

* 敏感变量（`JWT_SECRET` / `REDIS_PASSWORD` / `MYSQL_PASSWORD` / `RABBITMQ_DEFAULT_PASS`）**禁止使用** **`${VAR:}`** **空默认**，
  缺失必须显式启动失败（fail-fast），不得静默退化为空字符串。

* IDEA 启动前若 Run Configuration 残留旧环境变量（如 `JWT_SECRET`、`REDIS_PASSWORD`），
  先删除（Run → Edit Configurations → 环境变量），再 File → Reload All from Disk。

### 8.4 排查速查

| 现象                                    | 检查                                                             |
| ------------------------------------- | -------------------------------------------------------------- |
| IDEA 启动报 Redis WRONGPASS / 缺密钥        | `.env` 是否存在；Run Configuration 是否残留手填密钥；是否 Reload 配置            |
| 启动报缺密钥（Could not resolve placeholder） | `.env` 是否存在且含密钥；工作目录为 `backend/` 或项目根均可（dev 双候选路径自动定位根 `.env`） |
| 怀疑密钥漂移                                | `grep -rn '<密钥值>' .idea/ backend/src/main/resources/` 应无命中     |

***

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
