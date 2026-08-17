# AGENTS.md — AI 执行入口

> **每次执行任务前，必须先读本文件。**
> 本文件是 AI 助手（Codex / Qoder / 其他）的唯一对齐锚点。

---

## 1. 项目简介

**Industrial AI Hub** — 工业 AI 设备管理平台。
后端 Spring Boot 3.5 + MyBatis + MySQL，前端预留（Vue3），基础设施 Docker Compose 统一管理。
目标：从 Java 基础到 AI 集成的完整技术栈闭环（16 周路线图）。

---

## 2. 文档索引（按优先级）

| 优先级 | 文档 | 路径 | 作用 |
|:------:|------|------|------|
| ★★★ | 本文件 | `AGENTS.md` | AI 入口 + 行为约束 |
| ★★★ | 每日路线图 | `backend/DAILY_ROADMAP.md` | 总计划 + 每日任务 |
| ★★★ | 应用架构 | `docs/Architecture/Application-Architecture.md` | 技术栈 + 分层 + API |
| ★★☆ | 从零复刻指南 | `docs/SETUP.md` | 克隆 → 配置 → 运行 → 验证 全流程 |
| ★★☆ | 基础设施基线 | `docs/Architecture/Infrastructure-Baseline.md` | Docker/网络/端口规范 |
| ★★☆ | 架构决策记录 | `docs/decision-log/0001~0015` | 关键技术决策理由（含 0015 密钥 SSOT） |
| ★★☆ | Phase 3-A 计划 | `docs/plans/phase3-a-infrastructure-stabilization.md` | 基础设施稳定化任务分解 + 验收标准 |
| ★☆☆ | 每日日志 | `backend/DAILY/DayXXX.md` | 当天产出 + 明日计划 |
| ★☆☆ | 周复盘 | `backend/REVIEW/WeekXX.md` | 阶段性总结 |
| ★☆☆ | 审计报告 | `docs/reports/Architecture-Consistency-Report-v1.2.md` | 文档-代码一致性检查 |
| ★☆☆ | SQL 审计 | `docs/reports/SQL-Audit-Report.md` | SQL 文件一致性审计 |
| ★☆☆ | 技术债务 | `docs/TECH-DEBT.md` | 技术债务 SSOT 清单 |
| ★☆☆ | DB Changelog | `docs/decision-log/0012-database-changelog.md` | 数据库变更记录 |
| ★☆☆ | SQL README | `backend/src/main/resources/sql/README.md` | SQL 初始化说明 |

---

## 3. 当前状态

- **基线**：v2.1.0 已冻结（Tag: `v2.1.0`，Commit: `ec9a158`，Release Gate: **GO**）
- **阶段**：Phase 3 学习路线已收官（Redis/RabbitMQ/Docker/Linux 全部完成），下一步 Phase 4 AI 集成
- **分支**：`main`（codex/phase-3a 已合并，归档保留）
- **Phase 3-A 归档**：`docs/plans/phase3-a-infrastructure-stabilization.md` + `docs/reports/phase3-a-plan-audit*.md`
- **已完成**：Phase 3 中间件武装全部收官（Redis + RabbitMQ + Docker + Linux 部署，89/89 测试全绿，第三阶段检查点达成）
- **下一步**：Day 64 — OpenAI API 基础（进入第四阶段 AI 集成）
- **Baseline V2.1 内容**：JWT 生产环境要求通过 compose 注入密钥；测试环境通过 application-test.yml 提供隔离密钥、Spring Bean 清理、Profiles（dev/prod）、Actuator（仅 health）、Dockerfile（multi-stage + non-root）、compose backend 服务、启动冒烟测试（ApplicationContextLoadTest）、前端路由修复 + Dashboard 页面
- **已完成模块**：设备 CRUD、JWT/BCrypt 认证、RBAC 权限、分页查询、全局异常处理、@Valid 校验、报警规则引擎、AOP 操作日志、Postman 测试集、Vue 3 前端（登录/仪表盘/设备/报警/日志 6 页面）、Redis 缓存（Spring Cache + Redisson 分布式锁）、RabbitMQ 消息（工作队列/发布订阅/DLQ/延迟队列）、Docker 容器化 + Nginx 反代、Linux 部署
- **待实现**：前端工业化视觉升级（见 DESIGN.md）、ELK 日志（Day 101，可选）

---

## 4. 行为约束（必须遵守）

### 4.1 执行前
- [ ] 读本文件（AGENTS.md）
- [ ] 读 `DAILY_ROADMAP.md` 确认当前 Day 和任务
- [ ] 读最近一天的 `DAILY/DayXXX.md` 确认昨日进度和明日计划
- [ ] 如涉及架构变更，读 `Application-Architecture.md` + 相关 ADR

### 4.2 执行中
- **不做未要求的事** — 只完成当前 Day 的任务，不超前实现
- **保持文档同步** — 修改代码后，同步更新相关文档（架构文档、ADR、日志）
- **遵循分层架构** — Controller → Service → Mapper，不跳层
- **统一响应格式** — 所有 API 返回 `ApiResponse<T>`（`dev.reboot.dto.ApiResponse`）
- **代码风格** — 遵循 `.editorconfig`（4 空格缩进、UTF-8、LF 换行）
- **构造器注入** — 不使用 `@Autowired` 字段注入
- **命名规范** — 包名小写、类名 PascalCase、方法/变量 camelCase
- **多 AI 协作卫生** — 禁止提交 AI/IDE 产物（`.qoder/`/`.workbuddy/`/`.codex/`/`.cursor/`/`.idea/`/`.vscode/`/`.DS_Store` 等，已列入 `.gitignore`）；需要留档的分析/审计产物放入 `docs/reports/` 并显式提交，勿留在工作区或 `.tmp`/`.bak`

### 4.3 执行后
- [ ] 更新 `backend/DAILY/DayXXX.md`（今日产出 + 明日计划）
- [ ] 如有架构变更，更新 `Application-Architecture.md`
- [ ] 如有新决策，新增 `docs/decision-log/00XX-*.md`
- [ ] 更新本文件 §3「当前状态」
- [ ] Git commit（描述格式：`Day XXX: 简要说明`）

---

## 5. 技术栈锁定（防漂移）

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 25 LTS (Temurin) | 唯一运行时，不降级 |
| Maven | 3.9.6 | 通过 Maven Wrapper 锁定，不使用 4.x RC |
| Spring Boot | 3.5.0 | parent POM 锁定 |
| MyBatis Spring Boot | 3.0.5 | 显式声明 |
| MySQL Connector/J | 9.2.0 | parent 管理 |
| MySQL (Docker) | 8.4 | compose.yml 锁定 |
| Redis Stack | 7.4.0-v1 | compose.yml 锁定 |
| RabbitMQ | 4.0-management | compose.yml 锁定 |
| Nacos | 2.4.3 | 预留基础设施（不纳入当前路线，不启动） |
| Elasticsearch | 8.17.0 | compose.yml 锁定 |
| Jedis | 5.2.0 | Redis 客户端（Day 43） |
| Redisson | 3.39.0 | 分布式锁（Day 46） |
| Guava | 33.4.0-jre | RateLimiter 限流（Day 37） |
| PageHelper | 2.1.0 | 分页（Day 25） |
| Knife4j | 4.5.0 | API 文档（ADR 0013） |
| JJWT | 0.12.6 | JWT 认证（Day 23） |

> **规则**：未经讨论，不得升级任何依赖版本。新增依赖必须在 ADR 中记录理由。

---

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
│   └── src/main/java/
│       ├── dev/reboot/        # 主应用代码
│       │   ├── controller/    # REST 控制器
│       │   ├── service/       # 业务逻辑
│       │   ├── mapper/        # MyBatis Mapper
│       │   ├── entity/        # 实体类
│       │   ├── dto/           # DTO + ApiResponse
│       │   ├── config/        # 配置类
│       │   ├── security/      # JWT Filter + Auth/RateLimit 拦截器
│       │   ├── aop/           # 操作日志切面
│       │   ├── annotation/    # @RequireRole / @OperationLog
│       │   ├── enums/         # ErrorCode / RoleEnum
│       │   ├── exception/     # BusinessException + 全局异常处理
│       │   ├── mq/            # RabbitMQ 生产者/消费者
│       │   ├── rule/          # 报警规则引擎
│       │   └── util/          # JwtUtils 等工具类
│       └── code/day01~22/     # 学习代码（不修改）
├── docs/
│   ├── Architecture/          # 架构文档
│   ├── decision-log/          # ADR 决策记录
│   └── reports/               # 审计报告
├── mysql/                     # MySQL 配置 + 数据
├── redis/                     # Redis 配置 + 数据
└── ...                        # 其他中间件目录
```

---

## 7. 关键约定

- **数据库**：`reboot`，7 张表（user/role/user_role/device/device_data/alarm/operation_log）
- **API 前缀**：`/api/`
- **端口**：Spring Boot 8080，MySQL(Docker) 3307，Redis 6379
- **密码加密**：BCrypt（已实现，Day 23）
- **认证方式**：JWT — JwtUtils 生成/验证/解析 + AuthService 登录/注册（已实现，Day 23）
- **权限模型**：RBAC — 拦截器 AuthInterceptor + @RequireRole 注解（已实现，Day 24）
- **逻辑删除**：device 表 is_deleted + softDeleteById（已实现，Day 24 post-audit）

---

## 8. 密钥与敏感配置来源（SSOT，ADR 0015）

> **项目根目录 `.env` 是本地开发环境敏感配置的唯一事实源（Single Source of Truth）。**

### 8.1 加载机制

| 环境 | 密钥来源 | 机制 |
|------|----------|------|
| dev（IDEA / 命令行） | 根目录 `.env` | `application-dev.yml` 经 `spring.config.import` 自动读取（双候选 `../.env` / `./.env`，兼容 backend/ 与项目根两种工作目录）；**无需手工设置任何环境变量** |
| test | `application-test.yml` 隔离占位值 | 不读取本地 `.env` |
| prod（Docker 容器） | 容器环境变量 | `compose.yml` 经 `${VAR}` 插值注入；**不依赖本地 `.env`** |

### 8.2 优先级

```
dev : OS 环境变量 > .env > application.yml 默认值
docker : .env → Docker Compose → Container Environment
prod : 由部署环境注入，不依赖开发 .env
```

### 8.3 强制规则（违反即视为缺陷）

- 禁止将真实密钥提交 Git（`.env` 已入 `.gitignore`）。
- 禁止在 IDEA Run Configuration / 其他 IDE 配置中手工复制密钥（会导致漂移，见 ADR 0015 事故）。
- 禁止在 `application*.yml` 中硬编码密钥。
- `.env.example` 只保留变量名与说明，禁止真实密钥。
- 敏感变量（`JWT_SECRET` / `REDIS_PASSWORD` / `MYSQL_PASSWORD`）**禁止使用 `${VAR:}` 空默认**，
  缺失必须显式启动失败（fail-fast），不得静默退化为空字符串。
- IDEA 启动前若 Run Configuration 残留旧环境变量（如 `JWT_SECRET`、`REDIS_PASSWORD`），
  先删除（Run → Edit Configurations → 环境变量），再 File → Reload All from Disk。

### 8.4 排查速查

| 现象 | 检查 |
|------|------|
| IDEA 启动报 Redis WRONGPASS / 缺密钥 | `.env` 是否存在；Run Configuration 是否残留手填密钥；是否 Reload 配置 |
| 启动报缺密钥（Could not resolve placeholder） | `.env` 是否存在且含密钥；工作目录为 `backend/` 或项目根均可（dev 双候选路径自动定位根 `.env`） |
| 怀疑密钥漂移 | `grep -rn '<密钥值>' .idea/ backend/src/main/resources/` 应无命中 |

---

> 最后更新：2026-08-16 | 维护者：AI 助手 + hula0710
