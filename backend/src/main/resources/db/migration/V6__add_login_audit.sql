-- ===================================================================
-- Industrial AI Hub — 登录审计表（P1-02-A-5）
-- Version: 1.0 | Updated: 2026-08-23
-- 背景：登录安全事件（成功/失败/原因/来源）当前不可溯源（operation_log 的 LOGIN 行
--       user_id 恒 NULL、无 username/reason/UA）；本迁移新增独立登录审计表。
-- 说明：
--   - append-only：只新增表，不修改 V1-V5，不影响 checksum；
--   - 新库 V1→V6 与既有 V1-V5 库增量升级均适用；
--   - reason 枚举（服务端专用，不回传客户端）：
--     SUCCESS / INVALID_CREDENTIAL / INVALID_PASSWORD / ACCOUNT_DISABLED / ACCOUNT_LOCKED / RATE_LIMIT；
--   - 禁止保存 password / token / secret（安全边界）。
-- ===================================================================

CREATE TABLE IF NOT EXISTS `login_audit` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`     BIGINT       DEFAULT NULL             COMMENT '用户 ID（成功或存在用户时；不存在=NULL）',
    `username`    VARCHAR(64)  NOT NULL                 COMMENT '尝试登录的用户名（输入值）',
    `success`     TINYINT      NOT NULL DEFAULT 0       COMMENT '1=成功 0=失败',
    `ip_address`  VARCHAR(64)  DEFAULT NULL             COMMENT '客户端 IP（XFF/RemoteAddr）',
    `user_agent`  VARCHAR(512) DEFAULT NULL             COMMENT 'User-Agent',
    `reason`      VARCHAR(128) NOT NULL                 COMMENT '结果原因（服务端专用，见文件头枚举）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计时间',
    PRIMARY KEY (`id`),
    KEY `idx_login_audit_user_time` (`user_id`, `created_at`),
    KEY `idx_login_audit_created_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录审计表';
