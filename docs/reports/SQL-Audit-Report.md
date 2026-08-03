# SQL 文件一致性审计报告

> **日期**：2026-07-27 | **审计范围**：`backend/src/main/resources/sql/` 全部 3 个文件 | **数据库**：MySQL 8.4 (Docker) / 8.2 (本机)

---

## 一、文件概览

| # | 文件 | 行数 | 类型 | 依赖 |
|:---:|------|:---:|------|------|
| 1 | `init.sql` | 162 | 全量初始化脚本 (DDL + DML) | 无 |
| 2 | `migrate_v1.1.sql` | 64 | 增量迁移脚本 (ALTER TABLE) | 需 init.sql 先执行 |
| 3 | `mock_device_data.sql` | 62 | 测试数据填充 (DML) | 需 device + device_data 表存在 |

### 1.1 init.sql — 全量初始化

- 创建数据库 `reboot`（utf8mb4）
- 创建全部 7 张业务表（`CREATE TABLE IF NOT EXISTS`）
- 所有 CHECK 约束 **内联** 在 `CREATE TABLE` 语句中（共 8 个）
- `is_deleted` 逻辑删除字段及索引 **内联** 在 `device` 和 `user` 表中
- `device_data.data_value` 为 `DECIMAL(18,6)`
- 插入默认角色（ADMIN / OPERATOR / VIEWER）和默认管理员
- **完全自包含，可独立运行**

### 1.2 migrate_v1.1.sql — 增量迁移（问题文件）

设计意图：为已存在的 `reboot` 数据库补齐 v1.1 新增字段、约束和数据。

实际包含的操作：
- `device` 表添加 `is_deleted` 列和索引
- `user` 表添加 `is_deleted` 列和索引
- `device_data.data_value` 精度修正
- 插入默认管理员
- **8 个 CHECK 约束的 ALTER ADD CONSTRAINT**
- 所有 CHECK 约束名称与 `init.sql` **完全一致**

### 1.3 mock_device_data.sql — 测试数据

- 插入 1 台测试设备（PLC-001）
- 插入 48 条模拟数据（24 条温度 + 24 条压力，覆盖 24 小时）
- 无 `USE reboot` 语句，需显式指定数据库
- 纯测试用途，不参与 Schema 定义

---

## 二、冲突矩阵

| 冲突点 | init.sql | migrate_v1.1.sql | 结果 |
|--------|:-------:|:---------------:|------|
| `device.is_deleted` 列 | ✅ 内联 CREATE | ALTER ADD COLUMN | **重复** — 列已存在 |
| `user.is_deleted` 列 | ✅ 内联 CREATE | ALTER ADD COLUMN | **重复** — 列已存在 |
| `device_data.data_value DECIMAL` | ✅ 内联 CREATE | ALTER MODIFY COLUMN | **冗余** — 类型已正确 |
| `chk_device_type` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 重复约束名 |
| `chk_device_status` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| `chk_user_status` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| `chk_data_type` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| `chk_alarm_level` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| `chk_alarm_status` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| `chk_operation_type` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| `chk_target_type` | ✅ 内联 CHECK | ALTER ADD CONSTRAINT | **💥 冲突** — Error 3822 |
| 默认管理员 | 完整（含 password, status） | **缺 password 和 status** | Bug — ON DUPLICATE KEY UPDATE 不完整 |

### 结论

**migrate_v1.1.sql 的全部操作均已包含在 init.sql 中。** 若先执行 init.sql 再执行 migrate_v1.1.sql：

- 在 MySQL 8.4（Docker）上：`IF NOT EXISTS` 语法使前半部分静默跳过，但 **8 个 CHECK 约束的 ADD CONSTRAINT 全部失败**（Error 3822）
- 在 MySQL 8.2（本机）上：**第一行 `ADD COLUMN IF NOT EXISTS` 即失败**（Error 1064，语法不支持）

---

## 三、从零构建模拟验证

### 测试环境
- MySQL 8.2.0（本机，端口 3306）
- 数据库：`reboot_audit_test`（全新创建）

### 执行结果

| 步骤 | 操作 | 结果 |
|:---:|------|:---:|
| 1 | 执行 `init.sql`（数据库名替换为 `reboot_audit_test`） | ✅ **成功** — 7 张表，8 个 CHECK 约束 |
| 2 | 执行 `migrate_v1.1.sql` | ❌ **失败** — 第一行 `ADD COLUMN IF NOT EXISTS` 语法错误 |
| 3 | 手动单独测试 `ALTER TABLE device ADD CONSTRAINT chk_device_type ...` | ❌ **失败** — Error 3822: Duplicate check constraint name |

### 最终 Schema 验证

仅执行 `init.sql` 后，数据库状态：

```
7 张表：
  user, role, user_role, device, device_data, alarm, operation_log

8 个 CHECK 约束：
  chk_user_status, chk_device_type, chk_device_status,
  chk_data_type, chk_alarm_level, chk_alarm_status,
  chk_operation_type, chk_target_type

关键字段验证：
  ✅ user.is_deleted     TINYINT NOT NULL DEFAULT 0
  ✅ device.is_deleted   TINYINT NOT NULL DEFAULT 0
  ✅ device_data.data_value DECIMAL(18,6)
  ✅ 默认 admin 用户 + 3 个角色 + admin-ADMIN 关联
```

**结论：init.sql 单独执行即可产生确定且唯一的数据库结构。migrate_v1.1.sql 完全冗余。**

---

## 四、额外发现的问题

### 4.1 mock_device_data.sql 缺少 `USE` 语句
该文件未包含 `USE reboot;`，直接执行会报 `No database selected`。需显式指定数据库或由调用方传入。

### 4.2 本机 MySQL 版本偏差
- 项目规范（[AGENTS.md](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/AGENTS.md)）：MySQL 8.4 (Docker)
- 实际本机 MySQL：**8.2.0** — 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`（需 ≥ 8.0.29，但 8.2.0 实测不支持此语法变体）
- 如后续需要迁移脚本，必须在 Docker MySQL 8.4 上测试

### 4.3 init.sql 硬编码数据库名
`CREATE DATABASE IF NOT EXISTS reboot` 硬编码数据库名，不利于：
- 多环境隔离（dev/test/staging）
- CI/CD 自动化测试
- 建议：移除 `CREATE DATABASE` 语句，由运维层（compose.yml / .env）管理数据库创建

---

## 五、版本管理建议

### 5.1 当前评估：不适合团队协作

| 问题 | 风险等级 |
|------|:---:|
| init.sql 和 migrate_v1.1.sql 内容重叠，执行顺序不明确 | **高** |
| 无版本号标记机制，无法判断"当前 Schema 是否最新" | **高** |
| 迁移脚本缺乏幂等性保证（IF NOT EXISTS 不可移植） | **中** |
| mock 数据与 Schema 定义混在同一目录，缺少分类 | **低** |

### 5.2 推荐方案：SSOT + 命名规范

考虑到项目当前为单人学习项目，不建议引入 Flyway/Liquibase 的复杂度。推荐折中方案：

**方案 A（推荐 — 当前阶段）**：以 `init.sql` 为单一事实来源

```
sql/
├── init.sql              ← 唯一的 Schema 定义（全量，始终最新）
├── seed_test_data.sql    ← 测试数据（原 mock_device_data.sql 重命名）
└── archive/              ← 历史迁移脚本存档
    └── migrate_v1.1.sql  ← 移入存档，不再执行
```

- 每次 Schema 变更直接修改 `init.sql`，确保它始终反映最新结构
- 删除 `migrate_v1.1.sql`（已完全被 init.sql 覆盖）

**方案 B（多人协作时升级）**：Flyway 命名规范

```
sql/
├── V001__init_schema.sql
├── V002__add_device_is_deleted.sql
├── V003__fix_device_data_precision.sql
└── R__seed_test_data.sql     ← 可重复执行的种子数据
```

### 5.3 立即行动建议

1. **删除** `migrate_v1.1.sql`（或移入 `sql/archive/`）
2. **重命名** `mock_device_data.sql` → `seed_test_data.sql`，添加 `USE reboot;`
3. **移除** `init.sql` 中的 `CREATE DATABASE IF NOT EXISTS reboot`（由 compose.yml 管理）
4. **创建** `sql/README.md` 说明执行顺序

---

## 六、Changelog 位置与内容建议

### 6.1 位置评估

| 位置 | 优点 | 缺点 |
|------|------|------|
| `backend/DAILY/` | 与每日日志在一起，开发时顺手记录 | 偏过程记录，新人难定位 |
| `docs/` | 正式文档区，适合做长期演进记录 | 距 SQL 文件远，需跳转查找 |
| `backend/src/main/resources/sql/` | 与 SQL 同目录，就近查找 | 属于资源目录，不适合放文档类文件 |

**推荐**：`docs/decision-log/` — 复用已有的 ADR（Architecture Decision Record）模式。

理由：
- 项目已有 `docs/decision-log/0001~0010` 的 ADR 实践
- Schema 变更本质上是架构决策，放入 ADR 目录语义一致
- 同时在 `sql/` 下保留一个简洁的 `README.md` 指向 ADR

### 6.2 Changelog 内容模板

建议新建 `docs/decision-log/0012-database-changelog.md`：

```markdown
# ADR-0011: Database Schema Changelog

> 状态：活跃 | 日期：2026-07-27

## 变更记录

| 版本 | 日期 | 变更类型 | 说明 | 影响范围 | 回滚方案 |
|------|------|:---:|------|------|------|
| 1.0 | 2026-07-20 | CREATE | 初始 Schema：7 张表 | 全部表 | DROP DATABASE |
| 1.1 | 2026-07-24 | ALTER | user/device 加 is_deleted + 8 个 CHECK 约束 | user, device, alarm, device_data, operation_log | 执行 init.sql v1.0 |
| — | 2026-07-26 | SEED | 新增 mock_device_data.sql 测试数据 | device, device_data | DELETE FROM device_data; DELETE FROM device; |

## 当前 Schema 版本

**最新版本**: v1.1 → 见 `sql/init.sql`

## 变更规范

- 全量初始化：修改 `sql/init.sql` 确保其为最新
- 增量迁移：按 Flyway 命名规范创建 `sql/V###__description.sql`
- 种子数据：以 `R__` 前缀命名
- 每次变更同步更新本文档
```

---

## 七、总结

| 项 | 当前状态 | 建议 |
|----|:---:|------|
| init.sql | ✅ 自包含，可独立运行 | 移除硬编码 DB 名 |
| migrate_v1.1.sql | ❌ 完全冗余 + 冲突 | **删除** 或移入 archive |
| mock_device_data.sql | ⚠️ 缺少 USE 语句 | 重命名为 seed_test_data.sql + 补 USE |
| 文件组织 | ⚠️ 存在版本混淆风险 | 采用 SSOT 策略（方案 A） |
| Changelog | ❌ 不存在 | 新建 `docs/decision-log/0012-database-changelog.md` |
| 本机 MySQL | ⚠️ 8.2.0（非项目标准 8.4） | 迁移脚本在 Docker 8.4 上测试 |

---

> **审计人**：Codex (AI) | **复核人**：hula0710
