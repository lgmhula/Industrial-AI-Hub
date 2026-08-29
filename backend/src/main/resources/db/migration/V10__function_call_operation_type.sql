-- ===================================================================
-- Industrial AI Hub — Function Calling 操作日志类型扩展（Flyway V10）
-- Version: 1.0 | Updated: 2026-08-29
-- 背景：Day 68 新增 POST /api/ai/agents/device-status（AI 设备状态问答，
--       Spring AI @Tool 函数调用），AiController 端点以 operationType=FUNCTION_CALL
--       写入审计日志（@OperationLog {ret} 记录轮次/调用数，ADR 0023）。
--       V1/V3/V9 的 chk_operation_type CHECK 会拦截该取值，导致审计写入失败。
-- 处置：扩展 chk_operation_type 允许值增加 FUNCTION_CALL（target_type=AI 已由 V9 覆盖）。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE',
                                    'FUNCTION_CALL'));
