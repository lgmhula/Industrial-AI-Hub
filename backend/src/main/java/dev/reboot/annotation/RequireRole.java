package dev.reboot.annotation;

import dev.reboot.enums.RoleEnum;

import java.lang.annotation.*;

/**
 * 角色权限注解 —— 标记在 Controller 方法或类上。
 *
 * <p>被 {@link dev.reboot.security.AuthInterceptor} 拦截检查。
 * 类级注解对类内所有方法生效，方法级注解覆盖类级。</p>
 *
 * <pre>{@code
 * @RequireRole(RoleEnum.ADMIN)
 * @DeleteMapping("/{id}")
 * public ApiResponse<Void> delete(@PathVariable Long id) { ... }
 * }</pre>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许访问的角色列表，满足任一即可。 */
    RoleEnum[] value() default {};
}
