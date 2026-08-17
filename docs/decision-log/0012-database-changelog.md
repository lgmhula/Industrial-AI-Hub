# ADR-0012: Database Schema Changelog

> 状态：活跃 | 创建：2026-07-27 | 关联：[SQL 审计报告](../reports/SQL-Audit-Report.md)

## 变更记录

| 版本 | 日期 | 变更类型 | 说明 | 影响范围 |
|------|------|:---:|------|------|
| 1.0 | 2026-07-20 | CREATE | 初始 Schema：user/role/user_role/device/device_data/alarm/operation_log（7 张表） | 全部表 |
| 1.1 | 2026-07-24 | ALTER | user + device 加 is_deleted 逻辑删除、device_data.data_value DECIMAL(18,6)、8 个 CHECK 约束 | user, device, device_data, alarm, operation_log |
| — | 2026-07-27 | REFACTOR | 归档 migrate_v1.1.sql（已合入 init.sql）、重命名 mock → seed_test_data.sql、移除硬编码 DB 名、新建本 changelog | 无 Schema 变更 |
| 1.2 | 2026-07-25 | ALTER | Day 26 — 全局异常处理、参数校验（@Valid）、device 逻辑删除完善 | device |
| — | 2026-07-26 | SEED | Day 30 — 新增 test device + 48 条模拟温度/压力数据 | device, device_data |
| V1 | 2026-08-17 | FLYWAY | Flyway 基线（ADR 0019）：原 init.sql 迁移为 `db/migration/V1__baseline.sql`，7 表 + 8 CHECK | 全部表 |
| V2 | 2026-08-17 | SEED | Flyway 种子：原 seed_test_data.sql 迁移为 `V2__seed_test_data.sql`（20 用户/50 设备/12 告警/采集数据） | user, role, user_role, device, device_data, alarm |
| V3 | 2026-08-17 | ALTER | `chk_operation_type` CHECK 扩展 `ACKNOWLEDGE`/`RESOLVE`（修复告警确认/解决操作日志静默丢失，SQL 3819） | operation_log |

## 当前 Schema 版本

**Flyway 管理**（ADR 0019）—— `backend/src/main/resources/db/migration/`（V1 基线 + V2 种子 + V3 操作日志类型扩展）；应用启动时自动迁移，变更 = 新增 `V###__*.sql`。历史 `sql/init.sql` / `seed_test_data.sql` 保留为 SSOT 参考（内容与 V1/V2 一致）。

### 表清单

| # | 表名 | 用途 | 关键字段 |
|:---:|------|------|------|
| 1 | `user` | 用户 | is_deleted(CHECK), BCrypt password |
| 2 | `role` | 角色 | ADMIN/OPERATOR/VIEWER |
| 3 | `user_role` | 用户角色关联 | user_id + role_id 联合唯一 |
| 4 | `device` | 设备 | is_deleted(CHECK), device_type(CHECK) |
| 5 | `device_data` | 设备传感器数据 | DECIMAL(18,6), data_type(CHECK) |
| 6 | `alarm` | 告警 | alarm_level(CHECK), status(CHECK) |
| 7 | `operation_log` | 操作审计日志 | operation_type(CHECK), target_type(CHECK) |

### 约束清单（8 个 CHECK）

`chk_user_status`, `chk_device_type`, `chk_device_status`, `chk_data_type`, `chk_alarm_level`, `chk_alarm_status`, `chk_operation_type`, `chk_target_type`

## 变更规范

1. **全量初始化**：直接修改 `sql/init.sql`，保证其为最新
2. **增量迁移**：按 `V###__description.sql` 命名放入 `sql/`
3. **种子数据**：以 `seed_` 前缀或 `R__` 前缀
4. **每次变更**：同步更新本 changelog 和 `sql/README.md`
5. **历史存档**：过时的迁移脚本移入 `sql/archive/`

## 数据库连接

| 环境 | 端口 | 密码 | 用途 |
|------|:---:|------|------|
| Docker MySQL (compose) | 3307 | `1zxcvbnm` | 开发主库 |
| 本机 MySQL | 3306 | `1zxcvbnm` | 本地备用 |
| Docker Master | 13306 | `admin123456` | 主从写入 |
| Docker Slave1 | 13307 | `admin123456` | 主从只读 |
| Docker Slave2 | 13308 | `admin123456` | 主从只读 |

---

> 最后更新：2026-07-27 | 审计参考：[SQL-Audit-Report.md](../reports/SQL-Audit-Report.md)
