-- ===================================================================
-- Industrial AI Hub — 设备巡检日报操作日志类型扩展（Flyway V13）
-- Version: 1.0 | Updated: 2026-08-30
-- 背景：Day 83 Agent + MCP 联调新增 POST /api/ai/agents/inspection-report，
--       McpInspectionAgentService 通过 MCP 客户端工具自动巡检并生成日报，
--       @OperationLog 以 operationType=INSPECTION / targetType=MCP 写入审计。
--       target_type=MCP 已由 V12 放行，本迁移只扩展 chk_operation_type。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE',
                                    'FUNCTION_CALL','INGEST','MCP_SMOKE','INSPECTION'));
