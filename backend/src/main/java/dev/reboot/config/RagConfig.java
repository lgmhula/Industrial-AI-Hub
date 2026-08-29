package dev.reboot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 基础设施配置注册（ADR 0024）。
 *
 * <p>组件实现由 {@code dev.reboot.rag} 包内的 {@code @Component} 自动扫描；
 * 此处只负责把 {@link RagProperties} 注册为配置属性 Bean。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
}
