package dev.reboot.aop;

import dev.reboot.annotation.OperationLog;
import dev.reboot.mapper.OperationLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作日志 AOP 切面 —— 自动拦截 @OperationLog 注解并写入数据库。
 *
 * <p>无论方法成功或失败，均记录日志（失败时 description 追加 [失败]）。</p>
 *
 * @author hula0710
 * @since 2026-07-28
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogMapper operationLogMapper;

    public OperationLogAspect(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Around("@annotation(dev.reboot.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean failed = false;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            failed = true;
            throw e;
        } finally {
            try {
                recordLog(joinPoint, failed);
            } catch (Exception e) {
                log.error("操作日志记录失败: {}", e.getMessage());
            }
        }
    }

    private void recordLog(ProceedingJoinPoint joinPoint, boolean failed) {
        var annotation = getAnnotation(joinPoint);
        if (annotation == null) {
            return;
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();

        Object userIdObj = request.getAttribute("userId");
        Long userId = userIdObj != null ? Long.valueOf(userIdObj.toString()) : null;

        String desc = buildDescription(annotation.description(), joinPoint);
        if (failed) {
            desc = "[失败] " + desc;
        }

        var entity = new dev.reboot.entity.OperationLog();
        entity.setUserId(userId);
        entity.setOperationType(annotation.operationType());
        entity.setTargetType(annotation.targetType());
        entity.setTargetId(resolveTargetId(annotation.targetIdArg(), joinPoint.getArgs()));
        entity.setDescription(desc);
        entity.setIpAddress(getClientIp(request));

        operationLogMapper.insert(entity);
        log.info("操作日志: userId={}, op={}, target={}, failed={}",
                userId, annotation.operationType(), annotation.targetType(), failed);
    }

    private Long resolveTargetId(int targetIdArg, Object[] args) {
        if (targetIdArg < 0 || args == null || targetIdArg >= args.length) {
            return null;
        }
        Object value = args[targetIdArg];
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.matches("\\d+")) {
            return Long.valueOf(text);
        }
        return null;
    }

    private OperationLog getAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            var signature = (org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature();
            return signature.getMethod().getAnnotation(OperationLog.class);
        } catch (Exception e) {
            log.warn("Failed to resolve @OperationLog annotation: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 构建日志描述，支持 {0} {1} 占位符按方法参数位置替换。
     *
     * <p>对非基本类型参数只输出类名短名，避免将整个 DTO 的 toString() 拼入日志。</p>
     */
    private String buildDescription(String template, ProceedingJoinPoint joinPoint) {
        if (template == null || template.isEmpty()) {
            return joinPoint.getSignature().toShortString();
        }
        Object[] args = joinPoint.getArgs();
        String desc = template;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String val = formatArg(args[i]);
                desc = desc.replace("{" + i + "}", val);
            }
        }
        return desc;
    }

    /** 格式化参数值：简单类型直接 toString，复杂类型输出类名或提取 ID。 */
    private String formatArg(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof Number || arg instanceof String || arg instanceof Boolean) {
            return arg.toString();
        }
        // 尝试反射提取 id 字段
        try {
            var idField = arg.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object idVal = idField.get(arg);
            if (idVal != null) {
                return arg.getClass().getSimpleName() + "(id=" + idVal + ")";
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        // 兜底：类名
        return arg.getClass().getSimpleName();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
