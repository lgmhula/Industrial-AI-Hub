-- ===================================================================
-- Industrial AI Hub — 用户安全状态模型（P1-02-A-2）
-- Version: 1.0 | Updated: 2026-08-23
-- 背景：P1-02-A-1 的账号锁定仅存于 Redis（TTL 15min，重启即失、无管理员解锁）；
--       本迁移为 user 增加持久安全状态字段，作为锁定/审计的持久事实源：
--         failed_attempts   连续登录失败次数（成功/解锁归零）
--         locked_until      持久锁定截止时间（NULL=未锁定）
--         password_changed_at 最近改密时间（P1-02-A-4 旧 token 失效基准）
-- 说明：
--   - append-only：只新增列，不修改 V1/V3/V4，不影响 checksum；
--   - 新库 V1→V5 与既有 V1-V4 库增量升级均适用（ADD COLUMN + 默认值回填）；
--   - 与 P1-02-A-1 Redis 计数协同：Redis=快速熔断，DB=持久事实源；
--     成功登录/管理员解锁须同时清 DB 与 Redis（见 UserService/AuthService）。
-- ===================================================================

ALTER TABLE `user`
    ADD COLUMN `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数（P1-02-A-2，成功/解锁归零）',
    ADD COLUMN `locked_until` DATETIME NULL COMMENT '锁定截止时间（NULL=未锁定）',
    ADD COLUMN `password_changed_at` DATETIME NULL COMMENT '最近改密时间（P1-02-A-4 旧 token 失效基准）';
