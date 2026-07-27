# SQL 脚本目录

> 最后更新：2026-07-27 | 维护者：hula0710 + AI

## 目录结构

```
sql/
├── README.md              ← 本文件
├── init.sql               ← 数据库全量初始化（SSOT）
├── seed_test_data.sql     ← 测试种子数据（可重复执行）
└── archive/               ← 历史迁移脚本存档
    └── migrate_v1.1.sql   ← 已合入 init.sql，不再执行
```

## 数据库初始化

### 方式一：Docker Compose（推荐）

项目根目录执行：

```bash
docker compose up -d mysql
```

数据库 `reboot` 和 root 密码由 `compose.yml` + `.env` 统一管理，容器首次启动会自动执行 `mysql/init/` 下的脚本。

### 方式二：手动执行

```bash
# 1. 先创建数据库（如尚未创建）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS reboot DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"

# 2. 执行初始化
mysql -u root -p reboot < backend/src/main/resources/sql/init.sql

# 3. （可选）灌入测试数据
mysql -u root -p reboot < backend/src/main/resources/sql/seed_test_data.sql
```

### 方式三：IDEA 内执行

1. 打开 `init.sql`
2. 右键 → Run 'init.sql'
3. 在弹出的数据源选择中指定 `reboot` 数据库

## 命名规范

- **全量初始化**：`init.sql` — 始终反映最新 Schema（Single Source of Truth）
- **增量迁移**：`V###__description.sql` — 未来如多人协作，按 Flyway 命名
- **种子数据**：以 `R__` 或 `seed_` 前缀
- **历史存档**：放入 `archive/`

## 当前 Schema 版本

**v1.1** — 7 张表，8 个 CHECK 约束，逻辑删除 + 工业精度 DECIMAL(18,6)

详见 [Database Changelog](../../../docs/decision-log/0011-database-changelog.md)

## 注意事项

- **不要手动执行** `archive/migrate_v1.1.sql` — 已完全合入 init.sql，重复执行会报 Error 3822（Duplicate constraint）
- Schema 变更后，同步更新 `docs/decision-log/0011-database-changelog.md`
- 禁止在 SQL 中硬编码数据库名，统一由 compose.yml 管理

## Docker 初始化说明

`mysql/init/01_init.sql` 是 `backend/src/main/resources/sql/init.sql` 的符号链接。
Docker MySQL 容器首次启动时，`/docker-entrypoint-initdb.d/` 下的 `.sql` 文件按文件名排序执行：

1. `01_init.sql` — Schema 初始化（7 张表 + 约束 + 默认角色/管理员）
2. `02_test_data.sql` — 测试数据填充（20 用户 + 设备 + 角色关联）

> 已注释掉 `CREATE DATABASE` 和 `USE reboot`，Docker MySQL 通过 `MYSQL_DATABASE` 环境变量自动创建数据库。
