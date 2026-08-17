-- ===================================================================
-- Industrial AI Hub — 操作日志类型扩展（Flyway V3）
-- Version: 1.0 | Updated: 2026-08-17
-- 背景：AlarmController.acknowledge / resolve 的 @OperationLog 使用
--       ACKNOWLEDGE / RESOLVE，与 V1 的 chk_operation_type CHECK
--       （仅 CREATE/UPDATE/DELETE/LOGIN/EXPORT）冲突，导致这两类
--       操作日志插入失败（SQL 3819）且被 OperationLogAspect 吞掉
--       （"操作日志记录失败" ERROR），日志静默丢失。
-- 处置：扩展 CHECK 允许值，保留 ACKNOWLEDGE/RESOLVE 语义可区分。
-- ===================================================================

ALTER TABLE `operation_log` DROP CHECK `chk_operation_type`;

ALTER TABLE `operation_log`
    ADD CONSTRAINT `chk_operation_type`
        CHECK (`operation_type` IN ('CREATE','UPDATE','DELETE','LOGIN','EXPORT','ACKNOWLEDGE','RESOLVE'));
