-- ===================================================================
-- Industrial AI Hub — 数据库迁移 (v1.0 → v1.1)
-- 适用场景：已有 reboot 库，需补齐新字段
-- 如果表不存在则跳过（CREATE TABLE IF NOT EXISTS 已含全部定义）
-- ===================================================================

USE reboot;

-- 1. device 表新增逻辑删除字段
ALTER TABLE `device`
    ADD COLUMN IF NOT EXISTS `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除'
    AFTER `location`;

-- 2. device 表新增索引
ALTER TABLE `device`
    ADD INDEX IF NOT EXISTS `idx_is_deleted` (`is_deleted`);

-- 3. device_data.data_value 精度修正
ALTER TABLE `device_data`
    MODIFY COLUMN `data_value` DECIMAL(18,6) NOT NULL COMMENT '数据值（工业精度）';

-- 4. 默认管理员（密码: admin123）
-- BCrypt hash: $2b$10$4KMuiB2W7NzIXQpnrG2cW.DfqL1WL4woBUK1/pPzjmsqfluFJ6Bea
INSERT INTO `user` (`id`, `username`, `password`, `status`) VALUES
    (1, 'admin', '$2b$10$4KMuiB2W7NzIXQpnrG2cW.DfqL1WL4woBUK1/pPzjmsqfluFJ6Bea', 1)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

-- 5. user 表新增逻辑删除字段（v1.2: Day 25 review fix）
ALTER TABLE `user`
    ADD COLUMN IF NOT EXISTS `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除'
    AFTER `status`;

ALTER TABLE `user`
    ADD INDEX IF NOT EXISTS `idx_is_deleted` (`is_deleted`);

-- 6. device 表 device_type CHECK 约束 (v1.2: Day 25 DB audit)
ALTER TABLE `device`
    ADD CONSTRAINT `chk_device_type` CHECK (`device_type` IN ('PLC','SENSOR','CAMERA','ROBOT','OTHER'));

-- 7. alarm 表字段 CHECK 约束 (v1.2: Day 25 DB audit)
ALTER TABLE `alarm`
    ADD CONSTRAINT `chk_alarm_level` CHECK (`alarm_level` IN (1,2,3));

ALTER TABLE `alarm`
    ADD CONSTRAINT `chk_alarm_status` CHECK (`status` IN (0,1,2));

-- 8. 枚举字段 CHECK 约束 (v1.2: Day 25 DB audit P3)
ALTER TABLE `user`
    ADD CONSTRAINT `chk_user_status` CHECK (`status` IN (0,1));

ALTER TABLE `device`
    ADD CONSTRAINT `chk_device_status` CHECK (`status` IN (0,1,2));

ALTER TABLE `device_data`
    ADD CONSTRAINT `chk_data_type` CHECK (`data_type` IN ('TEMPERATURE','PRESSURE','SPEED','HUMIDITY','CURRENT'));

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type` CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT'));

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_target_type` CHECK (`target_type` IN ('USER','DEVICE','ALARM','ROLE'));
