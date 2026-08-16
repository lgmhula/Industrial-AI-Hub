# 从零复刻指南（Setup & Reproduction Guide）

> **目标**：任何人在全新环境（或任何 AI 代理）`git clone` 本仓库后，**无需猜测**，严格按本文件步骤即可从无到有完整跑起项目。
> **适用版本**：Baseline v2.1.0 + Phase 3 收官（Day 63，2026-08-16）
> **一句话版**：`cp .env.example .env` → `docker compose up -d` → `cd frontend && npm i && npm run dev` → 浏览器打开 `http://localhost:5173`，登录 `admin / admin123`。

---

## 0. 系统全景

| 组件 | 技术 | 版本 | 端口 |
|------|------|------|------|
| 后端 | Spring Boot 3.5 + MyBatis | JDK 25 | 8080 |
| 数据库 | MySQL (Docker) | 8.4 | 3307（宿主） |
| 缓存 | Redis Stack | 7.4.0-v1 | 6379 |
| 消息 | RabbitMQ | 4.0-management | 5672 / 15672 |
| 前端 | Vue 3 + Vite + Element Plus | Node 20+ | 5173 |
| API 文档 | Knife4j | 4.5.0 | `/doc.html`（仅 dev） |

> 其它可选基础设施（Nacos / MinIO / Elasticsearch / MySQL 主从 / Redis Sentinel）默认**不启动**，属于 `full` profile，见 §3.3。

---

## 1. 前置依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | **25 LTS (Eclipse Temurin)** | 唯一运行时，不降级。`java -version` 确认 |
| Docker | 24+（含 Compose v2） | `docker --version` + `docker compose version` |
| Node.js | 20+ | `node -v`，前端构建用 |
| Maven | 无需手动安装 | 项目内置 Maven Wrapper（`./mvnw`），自动下载 3.9.6 |

---

## 2. 克隆 + 环境变量

```bash
git clone <你的仓库地址> industrial-ai-hub
cd industrial-ai-hub

# 从模板生成 .env（必做，compose 依赖它）
cp .env.example .env
```

编辑 `.env`，**至少**确认/修改以下键：

| 键 | 说明 |
|----|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `RABBITMQ_DEFAULT_PASS` | RabbitMQ 密码 |
| `JWT_SECRET` | **≥32 字符**（HS256 需 256 bits），否则后端启动即失败 |

> `.env` 已被 `.gitignore` 排除，不会入库；`.env.example` 是唯一提交的模板。

---

## 3. 启动后端

### 3.1 路径 A — 全容器一键（推荐先"跑起来"）

```bash
docker compose up -d
```

默认启动 **4 个核心服务**：`mysql` / `redis` / `rabbitmq` / `backend`。
数据库 `reboot` 在 mysql 容器**首次启动时自动初始化**（`mysql/init/` 下的脚本，含 7 张表 + 默认角色 + admin 账户）。

验证：

```bash
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}
```

### 3.2 路径 B — 后端本地开发（热更，推荐开发期）

```bash
# 只起依赖中间件，后端在宿主机用 Maven Wrapper 跑
docker compose up -d mysql redis rabbitmq

cd backend
./mvnw spring-boot:run
# 应用启动在 http://localhost:8080
# dev profile 默认开启 Knife4j：http://localhost:8080/doc.html
```

> 后端默认 profile 是 `dev`（`application.yml`：`SPRING_PROFILES_ACTIVE` 缺省 `dev`）；容器内强制 `prod`（`compose.yml` 注入）。

### 3.3 全量 13 服务（可选）

```bash
docker compose --profile full up -d
```

额外启动 9 个 `full` profile 服务：`nacos` / `minio` / `elasticsearch` / `mysql-master` / `mysql-slave1` / `mysql-slave2` / `redis-sentinel1~3`。
> 这些是预留能力（Nacos/MinIO 未纳入 16 周主路线，ES 留给 Day 101 ELK 可选），日常开发无需启动。

---

## 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 前端：`http://localhost:5173`
- Vite 已配置 `/api` 代理 → `http://127.0.0.1:8080`（`vite.config.js`）
- 端口固定 5173（`strictPort: true`），后端 CORS 白名单默认只放行 5173，勿改端口

---

## 5. 登录验证

| 项 | 值 |
|----|----|
| 地址 | `http://localhost:5173` |
| 用户名 | `admin` |
| 密码 | `admin123` |

> 生产部署请立即修改 admin 密码。6 个前端页面：登录 / 仪表盘 / 设备 / 设备详情 / 报警 / 日志。

---

## 6. 数据库初始化说明

Docker 容器首次启动时，`compose.yml` 把 `./mysql/init/` 挂载到 `/docker-entrypoint-initdb.d/`，按文件名顺序执行：

1. `01_init.sql` — Schema 初始化（7 张表 + 8 CHECK 约束 + 默认角色/管理员）
2. `02_test_data.sql` — 测试数据（用户/设备/角色关联）

> ⚠️ **Windows 注意**：`mysql/init/01_init.sql` 是**符号链接**（指向 `backend/src/main/resources/sql/init.sql`）。Windows 克隆后若该文件退化为文本，请删除它，改执行手动初始化（见 `backend/src/main/resources/sql/README.md`「方式二」）。

手动初始化（跳过 Docker 自动执行时）：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS reboot DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"
mysql -u root -p reboot < backend/src/main/resources/sql/init.sql
```

---

## 7. 常见问题（Troubleshooting）

| 现象 | 原因 | 解法 |
|------|------|------|
| 后端启动即崩 | `JWT_SECRET` 为空或 <32 字符 | `.env` 里设置 ≥32 字符密钥 |
| 端口被占用 | 3307/6379/5672/8080/5173 冲突 | `lsof -i :端口` 排查，或改 `.env`/compose 端口映射 |
| `docker compose up` 报缺变量 | 未 `cp .env.example .env` | 执行 §2 |
| 前端登录 403 | CORS 白名单只放行 5173 | 保持 Vite 端口 5173，或改 `.env` `CORS_ORIGINS` |
| Knife4j 打不开 | 用了 `prod` profile | 本地开发用 `dev`（默认）或 `application-dev.yml` |
| 想全量起 13 服务 | 默认只起 4 核心 | `docker compose --profile full up -d` |
| MySQL 初始化脚本没跑 | 数据卷 `mysql/data` 已有旧数据 | 首次初始化只在**空数据卷**触发，删除旧卷或手动执行 SQL |

---

## 8. 文档索引（人/AI 快速定位）

| 想了解 | 看 |
|--------|----|
| 总计划 + 每日任务 | `backend/DAILY_ROADMAP.md` |
| 技术栈 + 分层 + API | `docs/Architecture/Application-Architecture.md` |
| Docker/网络/端口规范 | `docs/Architecture/Infrastructure-Baseline.md` |
| 关键技术决策 | `docs/decision-log/0001~0014` |
| 数据库变更记录 | `docs/decision-log/0012-database-changelog.md` |
| SQL 初始化说明 | `backend/src/main/resources/sql/README.md` |
| AI 执行入口 + 行为约束 | `AGENTS.md` |
