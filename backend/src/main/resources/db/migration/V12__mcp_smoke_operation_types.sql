-- ===================================================================
-- Industrial AI Hub — MCP 冒烟操作日志类型扩展（Flyway V12）
-- Version: 1.0 | Updated: 2026-08-30
-- 背景：Day 83 补 POST /api/mcp/smoke 审计（DG-002）。McpController 以
--       operationType=MCP_SMOKE / targetType=MCP 写入审计（运维诊断冒烟），
--       V1/V3/V9/V10/V11 的 CHECK 会拦截该取值，导致审计写入失败。
-- 处置：扩展 chk_operation_type 增加 MCP_SMOKE，chk_target_type 增加 MCP。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE',
                                    'FUNCTION_CALL','INGEST','MCP_SMOKE'));

ALTER TABLE `operation_log` DROP CHECK `chk_target_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_target_type`
        CHECK (`target_type` IN ('USER','DEVICE','ALARM','ROLE','AI','KNOWLEDGE','MCP'));
