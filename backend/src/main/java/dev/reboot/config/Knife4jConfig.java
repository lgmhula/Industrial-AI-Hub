package dev.reboot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / Swagger3 接口文档配置。
 *
 * <p>访问地址：{@code http://localhost:8080/doc.html}</p>
 *
 * @author hula0710
 * @since 2026-08-01
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI industrialAiHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Industrial AI Hub API")
                        .description("工业 AI 设备管理平台 — REST API 文档")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("hula0710")
                                .email("hula0710@github"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
