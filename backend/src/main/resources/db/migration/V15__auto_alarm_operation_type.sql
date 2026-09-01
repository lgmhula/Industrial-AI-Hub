-- ===================================================================
-- Industrial AI Hub — Day 86 AI 自动生成报警 操作日志类型扩展（Flyway V15）
-- Version: 1.0 | Updated: 2026-09-01
-- 背景：Day 86 AiAlarmAutoCreator.createAlarms() 以 operationType=AUTO_ALARM /
--       targetType=ALARM 写入审计（AI 巡检异常 → 业务报警 自动化），
--       V1/V3/V9/V10/V11/V12/V13/V14 的 CHECK 会拦截 operationType=AUTO_ALARM，
--       导致审计写入失败。
-- 处置：扩展 chk_operation_type 增加 AUTO_ALARM。
--       targetType=ALARM 已在 V3 基线中包含，无需修改。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE',
                                    'FUNCTION_CALL','INGEST','MCP_SMOKE','INSPECTION','PUSH',
                                    'AUTO_ALARM'));
