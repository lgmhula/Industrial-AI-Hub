-- ===================================================================
-- Industrial AI Hub — AI 操作日志类型扩展（Flyway V9）
-- Version: 1.0 | Updated: 2026-08-28
-- 背景：Day 67 给 AiController 三个端点补充 @OperationLog
--       （CHAT / SUMMARY / DIAGNOSE，targetType=AI），
--       V1/V3 的 CHECK 约束会拦截这些取值，导致 AI 审计日志静默丢失。
-- 处置：扩展 chk_operation_type 与 chk_target_type 允许值，
--       保持 AI 操作可独立检索（operation_type 语义区分）。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE'));

ALTER TABLE `operation_log` DROP CHECK `chk_target_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_target_type`
        CHECK (`target_type` IN ('USER','DEVICE','ALARM','ROLE','AI'));
