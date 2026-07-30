package dev.reboot.config;

import dev.reboot.security.AuthInterceptor;
import dev.reboot.security.JwtAuthFilter;
import dev.reboot.security.RateLimitInterceptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 —— 注册 JWT Filter + 权限拦截器 + 限流拦截器。
 *
 * <p>执行顺序：JWT Filter → RateLimit → AuthInterceptor → Controller。</p>
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

    @Bean
    public static JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter();
    }

    @Bean
    public static AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    /** 限流拦截器 Bean。 */
    @Bean
    public static RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(jwtAuthFilter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    /**
     * 拦截器注册 —— 限流先于权限。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流最优先
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .order(0);

        // 权限校验
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .order(1);
    }
}
