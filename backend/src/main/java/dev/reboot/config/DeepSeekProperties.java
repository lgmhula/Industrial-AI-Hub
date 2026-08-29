package dev.reboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek API 配置属性（Phase 4 AI 集成）。
 *
 * <p>默认关闭：未配置 API Key 时不影响核心业务启动；
 * 启用后由 {@link dev.reboot.client.DeepSeekClient} 在请求期做缺失校验。</p>
 *
 * @author AI 助手
 * @since 2026-08-28
 */
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** 是否启用 DeepSeek AI 能力。 */
    private boolean enabled = false;

    /** API Base URL（DeepSeek 官方 https://api.deepseek.com）。 */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key（SSOT：dev 来自根目录 .env）。 */
    private String apiKey = "";

    /** 默认模型：deepseek-chat（V3）或 deepseek-reasoner（R1）。 */
    private String model = "deepseek-chat";

    /** HTTP 连接/读取超时（秒）。 */
    private int timeoutSeconds = 30;

    /** 默认最大生成 token 数。 */
    private int maxTokens = 1024;

    /** 采样温度。 */
    private double temperature = 0.3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}
