# Decision 0016: Charset-Safe Database Init（中文种子数据乱码修复）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-17 |
| **决策者** | hula0710 + AI 助手（DSH 部署报告驱动） |
| **关联** | `mysql/init/01-init-db.sh` / `compose.yml` mysql 服务 / runbook 坑位 #11 |

---

## 1. 背景与事故

2026-08-17 新设备（Windows 11）首次部署，验收时发现 API/数据库中文种子数据乱码：

- `GET /api/devices` 返回 `deviceName":"莽鈥澛得ヅ犅..."`；
- 数据库 `device_name` 实际存储 `C3A6C2B8C2A9...`（双重编码），而非正确 UTF-8 `E6B8A9...`。

## 2. 根因（已独立验证）

1. 仓库 SQL 文件字节级为干净 UTF-8；
2. 官方 `mysql:8.4` 镜像 `docker-entrypoint.sh` 的 `docker_process_sql()` 命令为
   `mysql --defaults-extra-file=<passfile> --protocol=socket -uroot -hlocalhost --comments "$@"`，
   **全文无任何 `--default-character-set` 参数**（已核对 docker-library/mysql 8.4 源码）；
3. 首次初始化时 `.sql` 文件经该函数管道灌入，客户端连接字符集取协商默认值（非 utf8mb4），
   UTF-8 字节被按 latin1/cp1252 解读后转存 utf8mb4 列 → 双重编码；
4. 影响：`role` / `device` / `alarm` 中文种子数据；表结构与计数不受影响；后端 API 输出本身为干净 UTF-8（非后端缺陷）。

## 3. 决策

**compose 直挂 SQL 改为 charset-safe 包装脚本**：

- 新增 `mysql/init/01-init-db.sh`：以 `mysql --default-character-set=utf8mb4 ... < 文件`
  显式加载 `init.sql` + `seed_test_data.sql`；
- `compose.yml` mysql 服务 volumes：
  - 删除 `init.sql` / `seed_test_data.sql` 直挂 `/docker-entrypoint-initdb.d/`；
  - 新增 `./backend/src/main/resources/sql:/init-sql:ro`（SQL 只读目录）+ `./mysql/init/01-init-db.sh:/docker-entrypoint-initdb.d/01-init-db.sh:ro`；
- 字符集作为 **CLI 参数**传递：不依赖 `[client]` 配置文件（避免 Windows 挂载 world-writable
  导致选项文件被忽略的平台脆弱性）；
- 依赖 entrypoint `set -e`：加载失败即容器启动失败（fail-fast，不静默带病运行）。

## 4. 备选方案（未采纳）

| 方案 | 说明 | 未采纳原因 |
|------|------|------------|
| SQL 文件头加 `SET NAMES utf8mb4` | 简单 | 客户端侧转换发生在发送前，仅部分有效（副本实测结论） |
| 挂载 `[client] default-character-set` 配置文件 | 零脚本 | Windows 挂载文件 world-writable 时被客户端忽略（同 my.cnf 陷阱），平台脆弱 |
| 自定义完整 entrypoint | 可控性强 | 改动大、破坏官方镜像升级路径，收益不成比例 |

## 5. 影响

- 未来**全新初始化**（空数据卷）自动获得正确中文数据；
- 既有库（本机 / 副本）中的乱码演示数据需 `docker compose down -v` 后重灌（纯演示数据，零风险）；
- 手动执行路径（`mysql < init.sql`）建议同样加 `--default-character-set=utf8mb4`（已同步 sql/README.md）。
