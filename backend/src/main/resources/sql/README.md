# SQL 脚本目录

> 最后更新：2026-08-17 | 维护者：hula0710 + AI

## 说明

> ⚠️ **Schema 已由 Flyway 接管（ADR 0019）**：活动迁移脚本已移至
> `backend/src/main/resources/db/migration/`，由应用启动时经 JDBC 自动执行（utf8mb4，无乱码）。
> 本目录仅保留历史存档与说明。

## 目录结构

```
sql/
├── README.md              ← 本文件
└── archive/               ← 历史存档（不再执行）
    ├── migrate_v1.1.sql   ← 已合入 V1__baseline.sql，不再执行
    └── seed_test_data_v1_device1_legacy.sql  ← 旧 seed 存档
```

活跃迁移（在 `db/migration/`）：

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__baseline.sql` | Schema + 必需初始化（7 表 + 约束 + 默认角色/admin） |
| V3 | `V3__operation_log_check_types.sql` | 操作日志类型 CHECK 扩展（ACKNOWLEDGE/RESOLVE） |

> ⚠️ **演示/测试种子数据不在迁移链内**（ADR 0019 §5，P0）：原 `V2__seed_test_data.sql` 已退役，
> 唯一事实源为 `db/seed/dev/seed_demo_data.sql`，开发环境经 `scripts/seed-dev.sh` 显式执行（幂等）。

## 迁移机制（Flyway，ADR 0019）

- **全新库**：backend 首次启动 → Flyway 自动执行 V1 → V3（**不产生任何 Demo 数据**）；
- **既有库**：`baseline-on-migrate + baseline-version=2` 基线到 2（跳过 V1 重放），零干预升级；
- **既有已执行过旧 V2 的库**：`ignore-migration-patterns: "*:missing"` 容忍退役的 V2，正常启动；
- **未来变更**：新增 `V###__描述.sql`，所有环境自动升级；
- **charset**：经 JDBC（Connector/J 9 默认 UTF-8）执行，中文数据无双重编码（取代 ADR 0016 CLI 包装脚本）。

## 演示种子数据（仅限开发环境）

```bash
./scripts/seed-dev.sh            # 读取根目录 .env 连接参数，幂等灌入 Demo 数据
```

- seed 文件：`backend/src/main/resources/db/seed/dev/seed_demo_data.sql`（20 用户 + 50 设备 + 12 告警 + 采集数据 + 操作日志）；
- 位置在 Flyway locations（`classpath:db/migration`）之外，**任何环境启动都不会自动执行**；
- 生产环境禁止执行；可安全重复执行（按业务键 NOT EXISTS 守卫，不产生重复数据）。

## 手动执行（可选，仅调试用）

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS reboot DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"
mysql --default-character-set=utf8mb4 -u root -p reboot < backend/src/main/resources/db/migration/V1__baseline.sql
# 演示数据（仅开发）：
mysql --default-character-set=utf8mb4 -u root -p reboot < backend/src/main/resources/db/seed/dev/seed_demo_data.sql
```

## 命名规范

- **版本迁移**：`V<序号>__<描述>.sql`（Flyway 标准，严格递增，已执行不可改）
- **历史存档**：放入 `sql/archive/`

## 当前 Schema 版本

**v1.1** — 7 张表，8 个 CHECK 约束，逻辑删除 + 工业精度 DECIMAL(18,6)

详见 [Database Changelog](../../../../../docs/decision-log/0012-database-changelog.md)

## 注意事项

- **不要手动执行** `archive/migrate_v1.1.sql` — 已完全合入 V1，重复执行会报 Error 3822（Duplicate constraint）
- Schema 变更 = 新增 `V###__*.sql` + 同步更新 `docs/decision-log/0012-database-changelog.md`
- 已执行的迁移文件**禁止修改**（否则 checksum 校验失败）；变更新增版本
- 禁止在 SQL 中硬编码数据库名，统一由 `MYSQL_DATABASE` 环境变量管理
