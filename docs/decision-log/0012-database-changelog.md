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
| — | 2026-08-18 | SEED-ISOLATION | 演示种子退出 Flyway 链（ADR 0019 §5，P0）：`V2__seed_test_data.sql` 退役，迁移链收口为 V1+V3；演示数据移至 `db/seed/dev/seed_demo_data.sql`，经 `scripts/seed-dev.sh` 显式灌入（幂等，生产不执行） | 无 Schema 变更 |
| V4 | 2026-08-23 | ADD | 站点授权模型（P1-01，ADR 0020）：`site` 表 + `user_site` 表 + `device.site_id`（回填默认站点 `DEFAULT`，NOT NULL DEFAULT 1，idx_device_site_id） | site, user_site, device |
| V5 | 2026-08-26 | ALTER | 用户安全状态字段（status/is_deleted/updated_at）与索引 | user |
| V6 | 2026-08-26 | ADD | 登录审计表 `login_audit`（成功/失败 + IP + 原因，用户时间索引） | login_audit |
| V7 | 2026-08-27 | ALTER | alarm 审计字段（acknowledged/resolved/updated_at）、device 复合唯一约束修复、role 管理字段 | alarm, device, role |
| V8 | 2026-08-28 | ALTER | admin 默认弱密码升级（BCrypt） | user |
| V9 | 2026-08-28 | ALTER | AI 操作日志类型扩展：`chk_operation_type` 增加 `CHAT/SUMMARY/DIAGNOSE`，`chk_target_type` 增加 `AI`（TD-028，Day 67） | operation_log |

## 当前 Schema 版本

**Flyway 管理**（ADR 0019）—— `backend/src/main/resources/db/migration/`（V1 基线 + V3~V9 增量）；应用启动时自动迁移，变更 = 新增 `V###__*.sql`。演示/测试种子数据**不在迁移链内**：唯一事实源为 `db/seed/dev/seed_demo_data.sql`，开发环境经 `scripts/seed-dev.sh` 显式执行（幂等；原 `V2__seed_test_data.sql` 已于 2026-08-18 退役，见 ADR 0019 §5）。

### 表清单

| # | 表名 | 用途 | 关键字段 |
|:---:|------|------|------|
| 1 | `user` | 用户 | is_deleted(CHECK), BCrypt password |
| 2 | `role` | 角色 | ADMIN/OPERATOR/VIEWER |
| 3 | `user_role` | 用户角色关联 | user_id + role_id 联合唯一 |
| 4 | `device` | 设备 | is_deleted(CHECK), device_type(CHECK) |
| 5 | `device_data` | 设备传感器数据 | DECIMAL(18,6), data_type(CHECK) |
| 6 | `alarm` | 告警 | alarm_level(CHECK), status(CHECK) |
| 7 | `operation_log` | 操作审计日志 | operation_type(CHECK 含 AI), target_type(CHECK 含 AI) |

### 约束清单（8 个 CHECK，V9 扩展 AI 枚举）

`chk_user_status`, `chk_device_type`, `chk_device_status`, `chk_data_type`, `chk_alarm_level`, `chk_alarm_status`, `chk_operation_type`, `chk_target_type`

## 变更规范

1. **全量初始化**：直接修改 `sql/init.sql`，保证其为最新
2. **增量迁移**：按 `V###__description.sql` 命名放入 `sql/`
3. **种子数据**：演示/测试种子放 `backend/src/main/resources/db/seed/dev/seed_demo_data.sql`（**禁止放入迁移目录**），经 `scripts/seed-dev.sh` 显式执行（幂等）
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
