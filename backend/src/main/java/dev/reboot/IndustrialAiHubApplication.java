package dev.reboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Industrial AI Hub 主启动类。
 *
 * <p>从 Day 21 起项目正式迁移至 Spring Boot 3.5，
 * 替代此前独立的 MyBatis SqlSessionFactory 模式。</p>
 *
 * @author hula0710
 * @since 2026-07-19
 */
@SpringBootApplication
public class IndustrialAiHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndustrialAiHubApplication.class, args);
    }
}
