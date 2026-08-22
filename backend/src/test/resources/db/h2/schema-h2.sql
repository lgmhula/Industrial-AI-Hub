-- ===================================================================
-- Industrial AI Hub — H2 测试夹具 Schema（仅测试用，非正式迁移）
-- 用途: DevSeedDemoDataTest 在 H2(MODE=MySQL) 上验证 db/seed/dev/seed_demo_data.sql
--       的加载与幂等性（零外部依赖，ADR 0018）。
-- 与正式迁移 V1__baseline.sql 的关系:
--   - 列/类型/约束/唯一键完全镜像 V1（保证 seed SQL 语义一致）；
--   - 索引名按「表名_原索引名」去重：H2 要求索引名在 schema 内全局唯一，
--     MySQL 允许各表同名索引（如 idx_is_deleted 在 user/device 各一个），
--     V1 原样在 H2 上无法建表，因此正式链路的运行时验证走真实 MySQL
--     （MySqlSeedIsolationIT，RUN_MYSQL_IT=true 时执行）。
-- ===================================================================

CREATE TABLE IF NOT EXISTS `user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `username`     VARCHAR(64)  NOT NULL                 COMMENT '用户名',
    `password`     VARCHAR(256) NOT NULL                 COMMENT 'BCrypt 加密密码',
    `email`        VARCHAR(128) DEFAULT NULL             COMMENT '邮箱',
    `phone`        VARCHAR(20)  DEFAULT NULL             COMMENT '手机号',
    `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：1-启用 0-禁用',
    CONSTRAINT `chk_user_status` CHECK (`status` IN (0,1)),
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0-正常 1-已删除',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_user_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `role` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `role_name`    VARCHAR(64)  NOT NULL                 COMMENT '角色名称',
    `role_code`    VARCHAR(32)  NOT NULL                 COMMENT '角色编码：ADMIN/OPERATOR/VIEWER',
    `description`  VARCHAR(256) DEFAULT NULL             COMMENT '角色描述',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `user_role` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`      BIGINT  NOT NULL                 COMMENT '用户 ID',
    `role_id`      BIGINT  NOT NULL                 COMMENT '角色 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_ur_user_id` (`user_id`),
    KEY `idx_ur_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `device` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `site_id`      BIGINT       NOT NULL DEFAULT 1       COMMENT '站点 ID（P1-01）',
    `device_name`  VARCHAR(128) NOT NULL                 COMMENT '设备名称',
    `device_code`  VARCHAR(64)  NOT NULL                 COMMENT '设备编码（唯一）',
    `device_type`  VARCHAR(32)  NOT NULL                 COMMENT '设备类型：PLC/SENSOR/CAMERA/ROBOT/OTHER',
    CONSTRAINT `chk_device_type` CHECK (`device_type` IN ('PLC','SENSOR','CAMERA','ROBOT','OTHER')),
    `status`       TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：1-在线 0-离线 2-维护中',
    CONSTRAINT `chk_device_status` CHECK (`status` IN (0,1,2)),
    `ip_address`   VARCHAR(45)  DEFAULT NULL             COMMENT 'IP 地址',
    `port`         INT          DEFAULT NULL             COMMENT '端口号',
    `location`     VARCHAR(256) DEFAULT NULL             COMMENT '安装位置',
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0-正常 1-已删除',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`device_code`),
    KEY `idx_device_type` (`device_type`),
    KEY `idx_device_status` (`status`),
    KEY `idx_device_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

CREATE TABLE IF NOT EXISTS `device_data` (
    `id`           BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `device_id`    BIGINT          NOT NULL                 COMMENT '设备 ID',
    `data_type`    VARCHAR(32)     NOT NULL                 COMMENT '数据类型：TEMPERATURE/PRESSURE/SPEED/HUMIDITY/CURRENT',
    CONSTRAINT `chk_data_type` CHECK (`data_type` IN ('TEMPERATURE','PRESSURE','SPEED','HUMIDITY','CURRENT')),
    `data_value`   DECIMAL(18,6)   NOT NULL                 COMMENT '数据值（工业精度）',
    `unit`         VARCHAR(16)     DEFAULT NULL             COMMENT '单位：°C/MPa/RPM/%',
    `recorded_at`  DATETIME        NOT NULL                 COMMENT '采集时间',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    KEY `idx_dd_device_id` (`device_id`),
    KEY `idx_dd_recorded_at` (`recorded_at`),
    KEY `idx_dd_type_time` (`device_id`, `data_type`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备数据表';

CREATE TABLE IF NOT EXISTS `alarm` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `device_id`     BIGINT        NOT NULL                 COMMENT '设备 ID',
    `alarm_type`    VARCHAR(32)   NOT NULL                 COMMENT '告警类型：OVER_TEMP/UNDER_PRESSURE/OFFLINE',
    `alarm_level`   TINYINT       NOT NULL DEFAULT 1       COMMENT '告警级别：1-一般 2-重要 3-紧急',
    CONSTRAINT `chk_alarm_level` CHECK (`alarm_level` IN (1,2,3)),
    `alarm_message` VARCHAR(512)  NOT NULL                 COMMENT '告警描述',
    `status`        TINYINT       NOT NULL DEFAULT 0       COMMENT '状态：0-未处理 1-已确认 2-已解决',
    CONSTRAINT `chk_alarm_status` CHECK (`status` IN (0,1,2)),
    `triggered_at`  DATETIME      NOT NULL                 COMMENT '触发时间',
    `resolved_at`   DATETIME      DEFAULT NULL             COMMENT '解决时间',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_alarm_device_id` (`device_id`),
    KEY `idx_alarm_level` (`alarm_level`),
    KEY `idx_alarm_status` (`status`),
    KEY `idx_alarm_triggered_at` (`triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警表';

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`         BIGINT        DEFAULT NULL             COMMENT '操作用户 ID',
    `operation_type`  VARCHAR(32)   NOT NULL                 COMMENT '操作类型：CREATE/UPDATE/DELETE/LOGIN/EXPORT',
    CONSTRAINT `chk_operation_type` CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT')),
    `target_type`     VARCHAR(32)   NOT NULL                 COMMENT '目标类型：USER/DEVICE/ALARM/ROLE',
    CONSTRAINT `chk_target_type` CHECK (`target_type` IN ('USER','DEVICE','ALARM','ROLE')),
    `target_id`       BIGINT        DEFAULT NULL             COMMENT '目标 ID',
    `description`     VARCHAR(512)  DEFAULT NULL             COMMENT '操作描述',
    `ip_address`      VARCHAR(45)   DEFAULT NULL             COMMENT '操作 IP',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ol_user_id` (`user_id`),
    KEY `idx_ol_operation_type` (`operation_type`),
    KEY `idx_ol_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 必需初始化数据（镜像 V1 的角色与 admin，保证 seed 的角色分配可解析）
INSERT INTO `role` (`role_name`, `role_code`, `description`) VALUES
    ('管理员',  'ADMIN',    '系统管理员，拥有所有权限'),
    ('操作员',  'OPERATOR', '设备操作员，可查看和操作设备'),
    ('观察者',  'VIEWER',   '只读权限，仅可查看数据和报表')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

INSERT INTO `user` (`id`, `username`, `password`, `status`) VALUES
    (1, 'admin', '$2b$10$4KMuiB2W7NzIXQpnrG2cW.DfqL1WL4woBUK1/pPzjmsqfluFJ6Bea', 1)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`), `password` = VALUES(`password`), `status` = VALUES(`status`);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

-- ================================================================
-- P1-01：site / user_site 夹具（镜像 V4__add_site_scoping.sql）
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

INSERT INTO `site` (`site_name`, `site_code`, `description`) VALUES
    ('默认工厂', 'DEFAULT', '系统默认站点')
ON DUPLICATE KEY UPDATE `site_name` = VALUES(`site_name`);

CREATE TABLE IF NOT EXISTS `user_site` (
    `id`           BIGINT  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`      BIGINT  NOT NULL                 COMMENT '用户 ID',
    `site_id`      BIGINT  NOT NULL                 COMMENT '站点 ID',
    `role_id`      BIGINT  NOT NULL                 COMMENT '站点内角色 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_site` (`user_id`, `site_id`),
    KEY `idx_us_user_id` (`user_id`),
    KEY `idx_us_site_id` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站点角色表';
