-- ===================================================================
-- Industrial AI Hub — 站点授权模型（P1-01）
-- Version: 1.0 | Updated: 2026-08-23
-- 背景：P1-01 水平越权/资源归属审计确认系统为「纯 RBAC、零资源归属」，
--       device/alarm/device_data 无 owner 维度，任何 VIEWER 可读任意设备（BOLA）。
--       本迁移引入 Site（站点）资源作用域，建立层级：
--         User ── user_site ── Site ── Device ── Alarm / DeviceData
-- 说明：
--   - 只新增 schema（site / user_site 表 + device.site_id），不修改 V1/V3，不影响 checksum；
--   - 站点内角色分配（user_site 数据）属开发 seed（db/seed/dev）与后续授权逻辑阶段，不在本迁移；
--   - 既有库（含已有 V1-V3 history 的库）增量执行：CREATE TABLE + ADD COLUMN + 回填 + NOT NULL；
--   - 幂等性：表用 CREATE TABLE IF NOT EXISTS、默认站点用 ON DUPLICATE KEY UPDATE 兜底；
--     ALTER（ADD COLUMN/MODIFY/ADD INDEX）依赖 Flyway「每版本只执行一次」保证。
-- ===================================================================

-- ================================================================
-- 1. 站点表 (site)
-- ================================================================
CREATE TABLE IF NOT EXISTS `site` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `site_name`    VARCHAR(64)  NOT NULL                 COMMENT '站点名称',
    `site_code`    VARCHAR(32)  NOT NULL                 COMMENT '站点编码（唯一）',
    `description`  VARCHAR(256) DEFAULT NULL             COMMENT '站点描述',
    `address`      VARCHAR(256) DEFAULT NULL             COMMENT '站点地址',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_code` (`site_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点表';

-- ================================================================
-- 2. 用户-站点角色表 (user_site)
-- ================================================================
CREATE TABLE IF NOT EXISTS `user_site` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`      BIGINT  NOT NULL                 COMMENT '用户 ID',
    `site_id`      BIGINT  NOT NULL                 COMMENT '站点 ID',
    `role_id`      BIGINT  NOT NULL                 COMMENT '站点内角色 ID（role 表）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_site` (`user_id`, `site_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_site_id` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站点角色表';

-- ================================================================
-- 3. 默认站点（系统初始化；新库与既有库均需要，供设备回填归属）
-- ================================================================
INSERT INTO `site` (`site_name`, `site_code`, `description`) VALUES
    ('默认工厂', 'DEFAULT', '系统默认站点（迁移回填 + 未分组设备）')
ON DUPLICATE KEY UPDATE `site_name` = VALUES(`site_name`), `description` = VALUES(`description`);

-- ================================================================
-- 4. device 增加站点归属
--    顺序：加可空列 → 回填默认站点 → 收紧 NOT NULL（含默认值）→ 加索引
--    默认值说明：DEFAULT site 由本迁移在【空】site 表上首次插入，id 恒为 1；
--    `DEFAULT 1` 使未显式携带 site_id 的既有 INSERT 路径（如现有 dev seed）
--    无需修改即可自动归属默认站点（P1-01 迁移兼容性，见 docs/security/P1-01-baseline-audit.md §4.3）。
-- ================================================================
ALTER TABLE `device` ADD COLUMN `site_id` BIGINT NULL COMMENT '站点 ID';

UPDATE `device` SET `site_id` = (SELECT `id` FROM `site` WHERE `site_code` = 'DEFAULT')
WHERE `site_id` IS NULL;

ALTER TABLE `device` MODIFY COLUMN `site_id` BIGINT NOT NULL DEFAULT 1 COMMENT '站点 ID';

ALTER TABLE `device` ADD INDEX `idx_device_site_id` (`site_id`);
