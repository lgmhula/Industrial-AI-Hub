-- ===================================================================
-- Industrial AI Hub - 活动告警合并（V16）
-- Version: 1.0 | Updated: 2026-10-18
-- 背景：持续超限时每条遥测都创建一条 alarm，造成报警风暴。
-- 方案：同设备同类型的未解决告警合并；解决后再次触发创建新记录。
-- ===================================================================

ALTER TABLE `alarm` ADD COLUMN `repeat_count` INT NOT NULL DEFAULT 1
    COMMENT '活动周期内重复触发次数';
ALTER TABLE `alarm` ADD COLUMN `last_triggered_at` DATETIME NULL
    COMMENT '最近一次触发时间';
UPDATE `alarm` SET `last_triggered_at` = `triggered_at`
    WHERE `last_triggered_at` IS NULL;

-- 先归并存量活动重复项，避免创建唯一索引时失败。
-- 保留最早 ID 作为活动记录；其他重复项标记已解决但保留审计历史。
CREATE TEMPORARY TABLE `alarm_active_merge` (
    `device_id` BIGINT NOT NULL,
    `alarm_type` VARCHAR(32) NOT NULL,
    `keep_id` BIGINT NOT NULL,
    `repeat_count` INT NOT NULL,
    `last_triggered_at` DATETIME NOT NULL,
    `active_status` TINYINT NOT NULL,
    PRIMARY KEY (`device_id`, `alarm_type`)
) ENGINE=InnoDB;

INSERT INTO `alarm_active_merge`
    (`device_id`, `alarm_type`, `keep_id`, `repeat_count`, `last_triggered_at`, `active_status`)
SELECT `device_id`, `alarm_type`, MIN(`id`), COUNT(*), MAX(`triggered_at`), MIN(`status`)
FROM `alarm`
WHERE `status` IN (0, 1)
GROUP BY `device_id`, `alarm_type`;

UPDATE `alarm` a
JOIN `alarm_active_merge` m
  ON a.`id` = m.`keep_id`
SET a.`repeat_count` = m.`repeat_count`,
    a.`last_triggered_at` = m.`last_triggered_at`,
    a.`status` = m.`active_status`;

UPDATE `alarm` a
JOIN `alarm_active_merge` m
  ON a.`device_id` = m.`device_id`
 AND a.`alarm_type` = m.`alarm_type`
SET a.`status` = 2,
    a.`resolved_at` = COALESCE(a.`resolved_at`, m.`last_triggered_at`),
    a.`last_triggered_at` = m.`last_triggered_at`,
    a.`updated_at` = CURRENT_TIMESTAMP
WHERE a.`id` <> m.`keep_id`
  AND a.`status` IN (0, 1);

DROP TEMPORARY TABLE `alarm_active_merge`;

ALTER TABLE `alarm` MODIFY COLUMN `last_triggered_at` DATETIME NOT NULL
    DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次触发时间';
ALTER TABLE `alarm` ADD COLUMN `active_alarm_key` VARCHAR(96)
    GENERATED ALWAYS AS (
        CASE WHEN `status` IN (0, 1)
             THEN CONCAT(`device_id`, ':', `alarm_type`)
             ELSE NULL END
    ) STORED COMMENT '活动告警唯一键：解决后为 NULL';
ALTER TABLE `alarm` ADD UNIQUE KEY `uk_alarm_active_device_type` (`active_alarm_key`);
ALTER TABLE `alarm` ADD INDEX `idx_alarm_last_triggered_at` (`last_triggered_at`);
