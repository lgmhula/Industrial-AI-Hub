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
 * Web MVC 配置 — JWT Filter + RateLimit + AuthInterceptor。
 *
 * <p>Filter 和 Interceptor 均为 Spring Bean（构造器注入），
 * 无手动 new，支持完整依赖注入。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(JwtAuthFilter jwtAuthFilter,
                        AuthInterceptor authInterceptor,
                        RateLimitInterceptor rateLimitInterceptor) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    /** JWT Filter 注册 — 对所有 /api/* 生效。 */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(jwtAuthFilter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    /** 拦截器注册 — RateLimit → Auth。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .order(0);

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**")
                .order(1);
    }
}
