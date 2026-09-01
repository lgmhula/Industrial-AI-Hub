-- ===================================================================
-- Industrial AI Hub — 巡检日报 SSE 推送操作日志类型扩展（Flyway V14）
-- Version: 1.0 | Updated: 2026-09-01
-- 背景：Day 85 Phase 6 新增 GET /api/push/inspection SSE 端点（ADR 0031 §5.2/§9），
--       InspectionPushController 以 operationType=PUSH / targetType=SSE 写入审计
--       （建连事件），V1/V3/V9-V13 的 CHECK 会拦截该取值，导致审计写入失败。
-- 处置：扩展 chk_operation_type 增加 PUSH，chk_target_type 增加 SSE。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE',
                                    'FUNCTION_CALL','INGEST','MCP_SMOKE','INSPECTION','PUSH'));

ALTER TABLE `operation_log` DROP CHECK `chk_target_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_target_type`
        CHECK (`target_type` IN ('USER','DEVICE','ALARM','ROLE','AI','KNOWLEDGE','MCP','SSE'));
