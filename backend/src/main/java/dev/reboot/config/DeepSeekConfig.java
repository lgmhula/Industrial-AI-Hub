package dev.reboot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DeepSeek 基础设施配置 —— RestClient + Spring AI ChatClient + 配置属性注册。
 *
 * <p>Spring AI 显式注入 OpenAI 兼容协议 Bean（baseUrl 指向 DeepSeek），
 * 配置 SSOT 仍为 {@link DeepSeekProperties}，避免依赖 spring.ai.openai.* 属性导致双源漂移。
 * 未配置 API Key 时 Bean 仍可创建（不发起网络请求），请求期由 AiService 统一做启用校验。</p>
 *
 * @author AI 助手
 * @since 2026-08-28
 */
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfig {

    @Bean("deepSeekRestClient")
    public RestClient deepSeekRestClient(DeepSeekProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .requestFactory(requestFactory(properties.getTimeoutSeconds()))
                .build();
    }

    /** OpenAI 兼容 Chat Completions API（baseUrl = DeepSeek）。 */
    @Bean
    public OpenAiApi openAiApi(DeepSeekProperties properties) {
        return OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .restClientBuilder(RestClient.builder()
                        .requestFactory(requestFactory(properties.getTimeoutSeconds())))
                .build();
    }

    /** Spring AI ChatModel：默认模型/温度/最大 token，统一结构化 JSON 输出。 */
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi, DeepSeekProperties properties) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build())
                .build();
    }

    /** ChatClient：业务层唯一入口，未来切换 OpenAI / Ollama / Zhipu 零业务代码改动。 */
    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    private ClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(1, timeoutSeconds);
        factory.setConnectTimeout(Duration.ofSeconds(timeout));
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        return factory;
    }
}
