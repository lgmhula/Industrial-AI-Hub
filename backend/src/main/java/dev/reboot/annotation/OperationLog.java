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

    /** 操作类型：CREATE / UPDATE / DELETE / LOGIN / EXPORT / ACKNOWLEDGE / RESOLVE。 */
    String operationType();

    /** 目标类型：USER / DEVICE / ALARM / ROLE / DEVICE_DATA。 */
    String targetType();

    /** 操作描述，支持 SpEL 表达式，如 "删除设备 #{#id}"。 */
    String description() default "";
}
