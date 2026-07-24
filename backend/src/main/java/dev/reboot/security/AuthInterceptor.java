package dev.reboot.security;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.RoleEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 权限拦截器 —— 根据 @{@link RequireRole} 注解检查用户角色。
 *
 * <h3>判定规则</h3>
 * <ol>
 *   <li>方法无 @RequireRole → 放行（公开接口）</li>
 *   <li>未登录（无 userId attribute）→ 返回 401</li>
 *   <li>角色不在允许列表 → 返回 403</li>
 * </ol>
 *
 * @author hula0710
 * @since 2026-07-24
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        // 方法级注解优先，否则用类级
        RequireRole annotation = hm.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            return true; // 公开接口
        }

        // 检查登录状态
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            log.warn("未认证访问: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(mapper.writeValueAsString(
                    ApiResponse.error(401, "请先登录")));
            return false;
        }

        // 检查角色
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("roles");
        RoleEnum[] requiredRoles = annotation.value();

        for (String roleStr : roles) {
            RoleEnum userRole = RoleEnum.fromCode(roleStr);
            if (userRole == null) continue;
            for (RoleEnum required : requiredRoles) {
                if (userRole.isAtLeast(required)) {
                    return true;
                }
            }
        }

        log.warn("权限不足: userId={}, roles={}, required={}, {} {}",
                userIdObj, roles, requiredRoles,
                request.getMethod(), request.getRequestURI());
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(
                ApiResponse.error(403, "权限不足")));
        return false;
    }
}
