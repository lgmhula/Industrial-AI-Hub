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
 * <p>从 JwtAuthFilter 设置的 request attribute 中读取当前用户 ID。
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
        Object result = joinPoint.proceed();
        try {
            recordLog(joinPoint);
        } catch (Exception e) {
            log.error("操作日志记录失败: {}", e.getMessage());
        }
        return result;
    }

    private void recordLog(ProceedingJoinPoint joinPoint) {
        OperationLog annotation = getAnnotation(joinPoint);
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

        dev.reboot.entity.OperationLog entity = new dev.reboot.entity.OperationLog();
        entity.setUserId(userId);
        entity.setOperationType(annotation.operationType());
        entity.setTargetType(annotation.targetType());
        entity.setDescription(desc);
        entity.setIpAddress(getClientIp(request));

        operationLogMapper.insert(entity);
        log.info("操作日志: userId={}, op={}, target={}", userId, annotation.operationType(), annotation.targetType());
    }

    private OperationLog getAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            org.aspectj.lang.reflect.MethodSignature signature =
                    (org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature();
            java.lang.reflect.Method method = signature.getMethod();
            return method.getAnnotation(OperationLog.class);
        } catch (Exception e) {
            log.warn("Failed to resolve @OperationLog annotation: {}", e.getMessage());
        }
        return null;
    }

    private String buildDescription(String template, ProceedingJoinPoint joinPoint) {
        if (template == null || template.isEmpty()) {
            return joinPoint.getSignature().toShortString();
        }
        // 使用 args 按位置替换 {0} {1} 占位符
        Object[] args = joinPoint.getArgs();
        String desc = template;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                desc = desc.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return desc;
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
