# 新设备部署手册（Deploy Runbook — New Device）

> 适用：任意全新设备（Windows/macOS/Linux）从 0 部署 Industrial AI Hub 全栈。
> 本手册固化 2026-08-17 Windows 副本首次部署踩过的全部坑位与对策，
> **执行者无需再扫描排查**，按序执行即可。
> 适用提交：`main` ≥ `a7857ef`（含 `.gitattributes` / ADR 0015 / compose 直挂载 SQL / backend env 注入）。

---

## 0. 前置条件

| 项 | 要求 | 说明 |
|---|---|------|
| OS | Windows 10/11 / macOS / Linux | 已验证 Windows 11 |
| JDK | Temurin 25 LTS | 唯一运行时；无 winget 时用清华镜像手动装 |
| Node | ≥ 20（npm ≥ 10） | npmmirror / 官方源均可 |
| Docker | ≥ 24 + Compose v2 | Windows 用 WSL2 backend；macOS 用 OrbStack/Desktop |
| Maven | 无需安装 | 仓库内置 Wrapper（阿里云源，国内直连） |
| 网络 | GitHub / maven.aliyun.com / registry.npmjs.org 可达 | 镜像源限速属正常，重试即可 |

---

## 1. 强性建议（必须遵守，否则大概率踩坑）

- **S1 — 全新 clone，禁止复用旧工作区**：删除旧目录再 `git clone`。
  旧工作区可能停在历史提交（事故：2026-08-17 副本停在 Day 55 的 `5037409`，
  与当前 main 相差 6 个提交，导致 SETUP.md 缺失 / symlink 残留 / backend 缺 env 注入三个假缺陷）。
- **S2 — 验证版本**：clone 后 `git rev-parse --short HEAD` 必须 ≥ `a7857ef`；
  若小于，执行 `git pull origin main` 或重新 clone。
- **S3 — 删除本地 `compose.override.yml`**（如有）：当前 main 的 compose 已完整注入
  REDIS/RABBITMQ env，override 不再需要；该文件已被 `.gitignore` 拦截，勿提交。
- **S4 — 换行符交给 `.gitattributes`**：`mvnw` / `*.sh` 已强制 LF，
  **不要手工改行尾、不要改 autocrlf**，pull 后直接可用（否则容器 shebang 失效 exit 127）。
- **S5 — 密钥全部随机生成，禁止复用任何旧设备 `.env`**：
  `cp .env.example .env` 后，用 `openssl rand -base64 48` 等生成值替换全部 `change_me`。
  JWT_SECRET ≥ 32 字符。`.env` 已 gitignore，不会入库。

---

## 2. 一键部署指令（粘贴给本机 DSH / 任意 AI 执行）

```
目标：全新设备从 0 部署 Industrial AI Hub 全栈并完成 9/9 验收，按本仓库
docs/reports/deploy-runbook-new-device.md 执行，坑位已列明，不要重复扫描排查。

步骤：
1. 环境预检：git / java(25) / node(≥20) / docker+compose v2；缺失即安装
   （Windows: 清华镜像装 JDK25；npm 用 npmmirror；Docker 用 WSL2 backend）。
2. 全新 clone：删除旧工作区 → git clone git@github.com:lgmhula/Industrial-AI-Hub.git
   → git rev-parse --short HEAD 确认 ≥ a7857ef；读 AGENTS.md + docs/SETUP.md 对齐约定。
3. 生成 .env：cp .env.example .env；全部 change_me 用 openssl rand 生成随机值
   （JWT_SECRET ≥32 字符）。严禁复用旧设备密钥。
4. 端口预检：3307/6379/5672/8080/5173/8001/15672 空闲；冲突则改 .env 端口映射。
5. 清理旧卷（若有旧部署残留）：docker compose down -v --remove-orphans
6. docker compose up -d（mysql/redis/rabbitmq/backend 四核心），等待全部 healthy
   （MySQL 首次启动自动执行 init.sql + seed_test_data.sql）。
7. 验收：
   a. curl http://localhost:8080/actuator/health → {"status":"UP"}
   b. POST /api/auth/login（admin/admin123）→ code 200 + JWT
   c. cd backend && ./mvnw test → 89 run / 0 fail（不要改任何代码）
   d. cd frontend && npm ci && npm run build → 成功；npm run dev 后 curl 5173 → 200
   e. 可选：docker compose --profile full up -d → 13/13 healthy
8. 自愈边界：只允许改 .env / 端口映射 / 安装依赖 / 系统配置；
   禁止改 backend/frontend 业务代码；确认为仓库缺陷时停止并报告。

输出部署报告：环境信息、各服务状态、验收通过/失败清单、遗留问题。
```

---

## 3. 已踩坑清单（2026-08-17 实战，全部有对策，勿重新扫描）

| # | 坑 | 症状 | 根因 | 对策（已固化到仓库） |
|:--:|---|---|---|---|
| 1 | 副本停在旧提交 | SETUP.md 缺失 / symlink 问题 / backend 缺 env | 复用了旧工作区，未重新 clone | S1/S2：全新 clone + 校验 commit ≥ a7857ef |
| 2 | CRLF 破坏脚本 | Docker 构建 exit 127 / mvnw not found | Windows autocrlf 将 mvnw/*.sh 检出为 CRLF，shebang 失效 | `.gitattributes` 强制 LF（本次已提交）；pull 后无需手工处理 |
| 3 | symlink 检出退化 | MySQL 0 张表 | 旧版 `mysql/init/01_init.sql` 是 symlink，Windows 检出成文本 | 已删除 symlink，compose 直挂载 `backend/src/main/resources/sql/*.sql` |
| 4 | backend 连不上 Redis/RabbitMQ | 容器内 WRONGPASS / 连接拒绝 | 旧版 compose backend 漏注入 env | 新版 compose 已注入（L332-339）；删除本地 override |
| 5 | my.cnf 被 MySQL 忽略 | 配置不生效（非阻塞） | Windows bind mount 文件 world-writable，mysqld 静默忽略 | 已知低危，暂不修复；如在意：`docker exec mysql sh -c "chmod 644 /etc/mysql/conf.d/my.cnf" && docker restart mysql` |
| 6 | 沙箱 TLS 阻断 | HTTPS 全部 SEC_E_NO_CREDENTIALS | 受限令牌沙箱拦截 Schannel | 安装/拉取阶段使用 full-access 权限 |
| 7 | 旧部署残留卷 | 数据非全新初始化 | 旧卷未清 | `docker compose down -v --remove-orphans` 后再 up |
| 8 | 镜像源限速/断流 | 拉取慢、偶发失败 | 网络到 Docker Hub 抖动 | 重试即可；ES 2GB 镜像提前拉取 |
| 9 | seed 数据不符预期 | 用户数/表数不对 | 卷未清或 init 未执行 | 见 #7；正确结果：7 张表 + admin 等 21 个用户 |
| 10 | Vite chunk>500kB / npm audit high | 构建警告 | ECharts/Element Plus 按需引入不彻底；依赖告警 | 非阻塞，忽略（已入 TECH-DEBT #13） |

---

## 4. 验收清单（9/9）

- [ ] 环境预检通过（git/JDK25/Node20+/Docker）
- [ ] 全新 clone + commit ≥ a7857ef + 文档对齐
- [ ] .env 全随机密钥（JWT_SECRET ≥32 字符，零复用）
- [ ] 端口全部空闲
- [ ] compose up 四核心全部 healthy
- [ ] /actuator/health = UP；admin 登录返回 code 200 + JWT
- [ ] mvnw test = 89/89
- [ ] 前端 npm ci + build 成功；dev server 5173 HTTP 200
- [ ] （可选）--profile full 13/13 healthy

---

## 5. 维护约定

- 新坑出现 → 追加到 §3，并同步修仓库（优先从源头消除，而非让副本绕行）。
- 本手册与 `docs/SETUP.md` 并存：SETUP 讲"怎么做"，本手册讲"坑在哪"。
