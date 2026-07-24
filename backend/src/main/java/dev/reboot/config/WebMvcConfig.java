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
 * @author hula0710
 * @since 2026-07-24
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * JWT 认证 Filter —— 最先执行，对所有请求生效。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter() {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    /**
     * 权限拦截器 —— 在 Filter 之后执行，仅拦截 /api/**。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");  // 登录/注册无需鉴权
    }
}
