# ADR-0011: 数据库设计审计 — FK / device_data 模型 / 保留策略

**日期:** 2026-07-25  
**状态:** Accepted  
**审计范围:** 7 张表、无 FK、device_data 单值模型、无保留策略、device_type 自由文本、email UNIQUE

## D1: 无外键约束 — Accepted (不添加 FK)

**背景:** 全部 7 张表不存在 FOREIGN KEY 约束。业界常见做法是加 FK + ON DELETE CASCADE。

**决策:** 工业 AI Hub **不添加** FK 约束，理由：

1. **软删除为主** — Device 和 User 均使用 `is_deleted` 逻辑删除，物理 DELETE 极少发生
2. **审计追溯需求** — device_data、alarm、operation_log 是审计数据，即使关联对象被删除也必须保留
3. **代码层已有保护** — UserService.delete() 显式级联清理 user_role
4. **ON DELETE CASCADE 过度激进** — 会级联删除审计数据，不可接受；ON DELETE SET NULL 对软删除模式意义有限

**替代措施 (已实现):**
- Device: 逻辑删除 (`is_deleted=1`)，devices_data/alarms 通过 `is_deleted=0` filter 隐含关联
- User: 逻辑删除，UserRoleMapper.deleteByUserId() 显式级联
- operation_log: user_id 可为 NULL（已删除用户的操作日志保留但匿名化）

**后果:**
- 应用层负责引用完整性
- 数据迁移/手动 SQL 操作需额外谨慎
- 适合工业场景的审计合规需求

## D2: device_data 单值模型 — Accepted (保持现状)

**背景:** device_data 每条记录只有一个 data_value。设备同时采集温度+压力需要多条记录。

**决策:** 保持当前 `(device_id, data_type, data_value, recorded_at)` 模型。不采用宽表。

**理由:**
1. **设备异构性** — 相机不测温度，PLC 不测湿度。宽表会产生大量 NULL 列
2. **动态扩展** — 新指标无需 ALTER TABLE
3. **索引覆盖** — 复合索引 `(device_id, data_type, recorded_at)` 已覆盖核心查询模式
4. **MySQL 写性能** — 200 rows/min（50 设备 × 4 指标）在 MySQL 承受范围内
5. **工业时序数据库** — 如未来数据量突破阈值，应迁移至 TimescaleDB/ClickHouse，而非在 MySQL 内改变模型

**后果:**
- 跨指标查询需 `WHERE data_type IN (...)`，延迟可接受
- 存储空间略高于宽表（每行携带 device_id 冗余），但差异在 5% 内

## D3: 无数据保留策略 — Accepted (V1 不接受，V2 处理)

**背景:** device_data 无 TTL 或归档策略，长期运行数据无限增长。

**估算:**
- 50 设备 × 4 指标 × 1/分 = 200 rows/min
- 月增量: ~8.6M rows
- 年增量: ~105M rows / ~10 GB data + ~10 GB index

**决策:** V1 阶段不实施保留策略。V2 阶段考虑：
- MySQL 按月 RANGE 分区
- 6 个月热数据 + 冷数据归档至对象存储
- 或迁移至 TimescaleDB（自动分区 + 压缩 + 保留策略）

**后果:** V1 开发测试期间无需担心。生产部署前（Phase 5+）必须确定保留方案。

## D4: device_type 自由文本 — Fixed (已修复)

**问题:** `device_type VARCHAR(32)` 无约束，可插入任意值。

**修复:** 添加 CHECK 约束 `device_type IN ('PLC','SENSOR','CAMERA','ROBOT','OTHER')`。已更新 init.sql、migrate_v1.1.sql、运行 DB。

**额外:** 同步添加 `alarm_level IN (1,2,3)` 和 `alarm.status IN (0,1,2)` CHECK 约束。

## D5: email UNIQUE — Accepted (维持现状)

**背景:** user.email 有 UNIQUE 约束，可能限制多用户共用邮箱。

**分析:** 
- MySQL UNIQUE 允许任意数量 NULL（NULL != NULL 判定），仅非 NULL 值唯一
- 工业场景：无邮箱用户 email=NULL 不受影响
- 有邮箱用户邮箱唯一是合理业务约束

**决策:** 维持现状，不修改。

## 追加发现: init.sql user 表语法错误 — Fixed

上一轮编辑 init.sql 添加 `idx_is_deleted` 时漏了逗号（`uk_email` 后缺 `,`），已在本次修复。
