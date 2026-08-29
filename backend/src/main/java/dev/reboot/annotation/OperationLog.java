package dev.reboot.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解 —— 标记需要自动记录操作日志的 Controller 方法。
 *
 * <p>被标记的方法在执行后会由 {@link dev.reboot.aop.OperationLogAspect} 自动拦截并写入 operation_log 表。
 *
 * @author hula0710
 * @since 2026-07-28
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作类型：CREATE / UPDATE / DELETE / LOGIN / EXPORT / ACKNOWLEDGE / RESOLVE / CHAT / SUMMARY / DIAGNOSE / FUNCTION_CALL / INGEST。 */
    String operationType();

    /** 目标类型：USER / DEVICE / ALARM / ROLE / DEVICE_DATA / AI / KNOWLEDGE。 */
    String targetType();

    /** 目标 ID 参数下标（方法参数从 0 开始）；-1 表示不记录 targetId。 */
    int targetIdArg() default -1;

    /** 操作描述，支持占位符 {0} {1}（按方法参数位置替换）与 {ret}（返回值摘要，Day 68）。 */
    String description() default "";
}
