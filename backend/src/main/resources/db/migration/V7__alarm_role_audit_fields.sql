-- ===================================================================
-- Industrial AI Hub — 告警审计字段 + 唯一约束修复 + 角色管理字段（V7）
-- Version: 1.0 | Updated: 2026-08-23
-- 背景：
--   DB-01: alarm 表缺 acknowledged_at/acknowledged_by/resolved_by/updated_at
--   DB-02: uk_device_code 与软删除冲突（删除后无法复用编码）
--   DB-03: role 表缺 status/is_deleted/updated_at（角色无法禁用/删除）
-- ===================================================================

-- ================================================================
-- 1. alarm 表补审计字段
-- ================================================================
ALTER TABLE `alarm` ADD COLUMN `acknowledged_at` DATETIME NULL COMMENT '确认时间';
ALTER TABLE `alarm` ADD COLUMN `acknowledged_by` BIGINT NULL COMMENT '确认人 ID';
ALTER TABLE `alarm` ADD COLUMN `resolved_by` BIGINT NULL COMMENT '解决人 ID';
ALTER TABLE `alarm` ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `alarm` ADD INDEX `idx_alarm_acknowledged_by` (`acknowledged_by`);

-- ================================================================
-- 2. device 唯一约束修复（支持软删除后复用编码）
--    原: UNIQUE KEY uk_device_code (device_code)
--    新: UNIQUE KEY uk_device_code_deleted (device_code, is_deleted)
--    效果：删除设备后同编码可重新创建
-- ================================================================
ALTER TABLE `device` DROP INDEX `uk_device_code`;
ALTER TABLE `device` ADD UNIQUE KEY `uk_device_code_deleted` (`device_code`, `is_deleted`);

-- ================================================================
-- 3. role 表补管理字段
-- ================================================================
ALTER TABLE `role` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用';
ALTER TABLE `role` ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除';
ALTER TABLE `role` ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- role_code 唯一约束也需兼容软删除（删除后可复用编码）
ALTER TABLE `role` DROP INDEX `uk_role_code`;
ALTER TABLE `role` ADD UNIQUE KEY `uk_role_code_deleted` (`role_code`, `is_deleted`);

ALTER TABLE `role` ADD INDEX `idx_role_status` (`status`);
