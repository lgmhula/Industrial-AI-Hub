package dev.reboot.config;

import dev.reboot.security.AuthInterceptor;
import dev.reboot.security.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 —— 注册 JWT Filter + 权限拦截器。
 *
 * <p>Filter 和 Interceptor 均为 Spring Bean，支持依赖注入。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(JwtAuthFilter jwtAuthFilter, AuthInterceptor authInterceptor) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authInterceptor = authInterceptor;
    }

    /** JWT 认证 Filter Bean。 */
    @Bean
    public static JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter();
    }

    /** 权限拦截器 Bean。 */
    @Bean
    public static AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    /**
     * JWT 认证 Filter —— 最先执行，对所有 /api/* 请求生效。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(jwtAuthFilter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    /**
     * 权限拦截器 —— 在 Filter 之后执行，仅拦截 /api/**。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}
