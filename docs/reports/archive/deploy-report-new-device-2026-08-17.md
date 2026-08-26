# 新设备部署报告（Industrial AI Hub — 2026-08-17）

> 执行方式：全新重部署（删除旧工作区 → 全新 clone → 全新 .env → compose 四核心 → 全量验收）
> 部署提交：`fa8f1b8`（Day 63 跨平台交接修复，main 最新）
> 对齐文档：`AGENTS.md` / `docs/SETUP.md` / `docs/reports/deploy-runbook-new-device.md`

---

## 1. 环境信息

| 项                | 值                                                        |
| ---------------- | -------------------------------------------------------- |
| 操作系统             | Windows 11 专业版 10.0.26200                                |
| Git              | 2.55.0.windows.3                                         |
| JDK              | Temurin 25.0.4+7 LTS（JAVA_HOME=E:\tools\jdk-25.0.4+7）    |
| Node / npm       | v22.20.0 / 10.9.3                                        |
| Docker / Compose | 29.6.2（WSL2 backend）/ v5.3.1                             |
| 仓库               | git@github.com:lgmhula/Industrial-AI-Hub.git @ `fa8f1b8` |
| Maven            | Wrapper 内置 3.9.6（阿里云镜像）                                  |

## 2. 部署过程要点

- **S1/S2**：删除旧工作区（旧 HEAD=`5037409`，Day 56-57），全新检出 main=`fa8f1b8` ≥ `a7857ef` ✅
- **S3**：无 `compose.override.yml`（新版 compose 已内置 env 注入）✅
- **S4**：`.gitattributes` 强制 LF，`mvnw` 检出 eol=lf，未手工改行尾 ✅
- **S5**：`.env` 由 CSPRNG 生成全随机 hex 密钥（JWT_SECRET 96 字符 ≥32；全部 change_me 清零；零复用旧设备密钥）✅
- 端口预检：3307/6379/5672/8080/5173/8001/15672 全部空闲 ✅
- `docker compose down -v --remove-orphans` 后 `up -d --build`：四核心全部 healthy

## 3. 服务状态（docker compose ps）

| 服务                                                                                             | 状态                                   |
| ---------------------------------------------------------------------------------------------- | ------------------------------------ |
| mysql                                                                                          | healthy（重启后复验通过）                     |
| redis                                                                                          | healthy                              |
| rabbitmq                                                                                       | healthy                              |
| backend (iah-backend)                                                                          | healthy（prod profile，compose 注入 env） |
| nacos / minio / elasticsearch / mysql-master / mysql-slave1 / mysql-slave2 / redis-sentinel1~3 | full profile（见 §4 验收 9）              |

## 4. 验收清单（9 项）

| #   | 验收项                                           | 结果  | 证据                                                                                               |
|:---:| --------------------------------------------- |:---:| ------------------------------------------------------------------------------------------------ |
| 1   | 环境预检通过（git/JDK25/Node20+/Docker+Compose v2）   | ✅   | §1                                                                                               |
| 2   | 全新 clone + commit ≥ fa8f1b8 + 文档对齐            | ✅   | HEAD=`fa8f1b8`                                                                                   |
| 3   | .env 全随机密钥（JWT_SECRET ≥32，零复用）                | ✅   | JWT_SECRET=96 字符；change_me 残留 0（仅注释提及）                                                           |
| 4   | 端口全部空闲                                        | ✅   | 7 端口预检 free                                                                                      |
| 5   | compose up 四核心全部 healthy                      | ✅   | mysql/redis/rabbitmq/backend 全 healthy                                                           |
| 6   | /actuator/health=UP；admin 登录 code 200 + JWT   | ✅   | `{"status":"UP"}`；login HTTP 200 / code 200 / HS512 JWT（sub=admin, roles=[ADMIN]）；mysql 重启后复验仍通过 |
| 7   | mvnw test 89/89                               | ✅   | Tests run: 89, Failures: 0, Errors: 0, BUILD SUCCESS（未改任何代码）                                     |
| 8   | 前端 npm ci + build 成功；dev server 5173 HTTP 200 | ✅   | npm ci 121 包；`✓ built in 749ms`；http://localhost:5173 → HTTP 200                                 |
| 9   | （可选）--profile full 13/13 healthy              | ✅   | 13/13 healthy（见 §5.3）                                                                            |

**9 项说明**：full profile 全量拉起后 13/13 healthy（elasticsearch / backend / minio / mysql / mysql-master / mysql-slave1 / mysql-slave2 / nacos / rabbitmq / redis / redis-sentinel1~3）。过程中遇到两个环境性问题并解决，详见 §5.3。

## 5. 遗留问题

### 5.1 【重要·仓库缺陷】中文种子数据乱码（查询数据乱码，用户指令第五节）

**现象**：API/数据库中的中文数据乱码。例如 `GET /api/devices` 返回 `deviceName":"莽鈥澛得ヅ犅..."`；数据库中 `温控传感器-01` 实际存储字节为 `C3A6C2B8C2A9C3A6C5BDC2A7...`（双重编码），而非正确 UTF-8 `E6B8A9E68EA7E4BCA0E6849FE599A8...`。

**根因链（已实验复现）**：

1. 宿主 SQL 文件（`backend/src/main/resources/sql/init.sql`、`seed_test_data.sql`）字节级验证为**干净 UTF-8**（`温`=E6B8A9 存在，双重编码形式不存在）；
2. mysql 镜像 `docker-entrypoint.sh` 的 `docker_process_sql()` **不带 `--default-character-set`**，仅 `--defaults-extra-file ... --comments`；
3. 无 charset 参数的 mysql 客户端默认以 **latin1 协商连接**（MySQL 的 latin1 即 cp1252，0x80-0x9F 区映射 €„†Ž…™Ÿ 等字符）；
4. 容器首次启动执行 `/docker-entrypoint-initdb.d/01_init.sql`、`02_seed_test_data.sql` 时，UTF-8 文件字节被按 latin1/cp1252 解读，再转换为 utf8mb4 列存储 → 双重编码入库；
5. 复现实验：用同样不带 `--default-character-set` 的客户端插入 `温控传感器-01`，存储字节与种子数据逐字节一致（`C3A6C2B8C2A9...`）。

**影响面**：`role`（管理员/操作员/观察者）、`device`（device_name/location 50 条）、`alarm`（alarm_message）等含中文种子数据；表结构/计数（7 表、21 用户、50 设备、12 告警、78 数据行）不受影响；后端自身输出（如登录消息"登录成功"）为干净 UTF-8，非后端缺陷。

**建议修复（仓库侧，本次未执行——自愈边界禁止修改 backend/frontend 业务代码与配置）**：

- 方案 A（推荐）：compose 挂载自定义 init 脚本（如 `01-init.sh`），内部以 `mysql --default-character-set=utf8mb4 ... < /docker-entrypoint-initdb.d/*.sql` 执行，替换入口直挂 SQL；
- 方案 B：`seed_test_data.sql`/`init.sql` 文件头加 `SET NAMES utf8mb4;`（不解决客户端 CP1252 解读，仍需配合客户端 charset 指定，仅部分有效）；
- 方案 C：compose 改用自定义 entrypoint/包装脚本统一注入 charset。

**本次处置**：按自愈边界仅验证与记录，未改仓库文件、未改写数据；如需要可后续手动以 `--default-character-set=utf8mb4` 客户端重灌种子数据恢复正确内容（属数据级修复，非代码/配置变更）。

### 5.2 my.cnf world-writable 被 MySQL 忽略（非阻塞，runbook 坑位 #5）

- 现象：`mysqld: [Warning] World-writable config file '/etc/mysql/conf.d/my.cnf' is ignored.`
- 说明：runbook 建议的 `chmod 644` 在**当前 compose 以 `:ro` 只读挂载**下不可行（`chmod: Read-only file system`），维持忽略状态；MySQL 8.4 默认即 utf8mb4，实测 `character_set_server/database/connection` 均 utf8mb4，无实际影响。

### 5.3 构建警告（非阻塞，TECH-DEBT #13）

- Vite 产物 `Dashboard` 602KB / `install` 530KB chunk >500kB 警告（ECharts/Element Plus 按需引入不彻底）；
- npm audit 1 high（未阻断，`npm ci --no-audit` 跳过审计）。

### 5.4 full profile 过程中的环境性问题（均已解决）

1. **compose 网络 id 错位（redis-sentinel 反复 "network ... not found"）**：
   首次 `--profile full up` 时哨兵容器引用了已被重建的旧网络 id（`2a7ae8...`），导致反复启动失败；
   彻底清理（删除全部容器 + `docker network rm industrial-network`）后重新 `up -d` 即恢复，属 Docker Desktop 网络重建竞态（runbook 坑位 #8 类），非仓库问题。
2. **elasticsearch 数据目录无写权限（bind mount 归属 root:root）**：
   ES 容器崩溃重启，报 `failed to obtain node locks ... AccessDeniedException node.lock`；
   根因是 Docker daemon 自动创建的 `./elasticsearch` 宿主目录在 WSL2 VM 内归属 root(0:0)（drwxr-xr-x），ES(uid 1000) 不可写；
   处置（系统级，非仓库改动）：`docker run --rm -v .../elasticsearch:/data alpine chown -R 1000:1000 /data` → restart 后 healthy（cluster green）。
   ⚠️ 注意：全新 clone 后该目录被 daemon 重建时会复现，需再次 chown（已属 runbook 潜在新坑，建议补充）。

---

## 6. 结论

四核心 + full profile 全部验收通过：**9/9 通过**（含可选第 9 项 13/13 healthy），后端 89/89 测试、前端构建 + 5173 HTTP 200、健康检查与 admin 登录均通过。**唯一实质遗留为 §5.1 中文种子数据乱码（仓库 init 机制缺陷，已定位根因并给出修复建议）**，其余为已知非阻塞项与本次解决的环境性事项。

> 生成时间：2026-08-17 | 维护者：AI 助手（DSH 部署）
