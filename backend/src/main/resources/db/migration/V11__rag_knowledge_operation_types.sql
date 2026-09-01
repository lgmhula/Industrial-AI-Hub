-- ===================================================================
-- Industrial AI Hub — RAG 知识库操作日志类型扩展（Flyway V11）
-- Version: 1.0 | Updated: 2026-08-29
-- 背景：Day 74 新增 POST /api/rag/documents（PDF 知识文档导入），
--       RagController 以 operationType=INGEST / targetType=KNOWLEDGE 写入审计，
--       V1/V3/V9/V10 的 CHECK 会拦截该取值。
-- 处置：扩展 chk_operation_type 增加 INGEST，chk_target_type 增加 KNOWLEDGE。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT',
                                    'ACKNOWLEDGE','RESOLVE','CHAT','SUMMARY','DIAGNOSE',
                                    'FUNCTION_CALL','INGEST'));

ALTER TABLE `operation_log` DROP CHECK `chk_target_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_target_type`
        CHECK (`target_type` IN ('USER','DEVICE','ALARM','ROLE','AI','KNOWLEDGE'));
