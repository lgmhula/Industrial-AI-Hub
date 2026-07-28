package dev.reboot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * 跨域配置 —— 允许前端开发时独立运行在不同端口。
 *
 * <p>生产环境应通过环境变量 CORS_ORIGINS 指定白名单域名。
 * 开发默认开放 localhost 系列端口。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 从环境变量读取允许的 Origin，默认开发环境
        String origins = System.getenv().getOrDefault("CORS_ORIGINS",
                "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173");
        config.setAllowedOriginPatterns(
                List.of(origins.split(",")));

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        log.info("CORS 已配置，允许 Origin: {}", origins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
