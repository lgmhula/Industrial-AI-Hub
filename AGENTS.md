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
| ★★☆ | 基础设施基线 | `docs/Architecture/Infrastructure-Baseline.md` | Docker/网络/端口规范 |
| ★★☆ | 架构决策记录 | `docs/decision-log/0001~0010` | 关键技术决策理由 |
| ★☆☆ | 每日日志 | `backend/DAILY/DayXXX.md` | 当天产出 + 明日计划 |
| ★☆☆ | 周复盘 | `backend/REVIEW/WeekXX.md` | 阶段性总结 |
| ★☆☆ | 审计报告 | `docs/reports/Architecture-Consistency-Report.md` | 文档-代码一致性检查 |

---

## 3. 当前状态

- **阶段**：第二阶段 — Industrial AI Hub V1（第 4-6 周）
- **进度**：Day 26 已完成，下一步 Day 31
- **Day 25 任务**：用户管理模块 + 分页查询（PageHelper） + 用户状态管理
- **已完成模块**：设备 CRUD、JWT、BCrypt、ApiResponse、CORS、RBAC、UserRoleMapper、User管理、PageHelper分页、GlobalExceptionHandler、@Valid校验
- **待实现**：前端页面

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
| Nacos | 2.4.3 | compose.yml 锁定 |
| Elasticsearch | 8.17.0 | compose.yml 锁定 |

> **规则**：未经讨论，不得升级任何依赖版本。新增依赖必须在 ADR 中记录理由。

---

## 6. 项目结构速查

```
Industrial-AI-Hub/
├── AGENTS.md                  ← 你正在读的文件
├── compose.yml                # Docker 基础设施（12 服务）
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
│       │   └── config/        # 配置类
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

> 最后更新：2026-07-25 | 维护者：AI 助手 + hula0710
