-- ===================================================================
-- V8: 更新 admin 密码（弱密码 → 强密码）
--
-- 背景：V1__baseline.sql 创建 admin 时使用弱密码，
-- 本迁移将其更新为与开发种子一致的强密码 BCrypt 哈希。
-- 同时重置安全状态（failed_attempts/locked_until），确保迁移后可立即登录。
-- ===================================================================

UPDATE `user`
SET `password` = '$2a$10$bqhBCq7qlhpegKHUgPaxhuqm.8EBQunBZvPTD8/HfnWjTC4C5Lnje',
    `failed_attempts` = 0,
    `locked_until` = NULL
WHERE `username` = 'admin' AND `is_deleted` = 0;
