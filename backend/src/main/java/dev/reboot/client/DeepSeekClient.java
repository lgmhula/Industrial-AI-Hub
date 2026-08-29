package dev.reboot.client;

import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.DeepSeekChatRequest;
import dev.reboot.dto.ai.DeepSeekChatResponse;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * DeepSeek Chat Completions HTTP 客户端（OpenAI 兼容协议）。
 *
 * <p>第三方 API 异常统一转为 503 BusinessException，避免泄露上游响应细节。</p>
 *
 * @author AI 助手
 * @since 2026-08-28
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final DeepSeekProperties properties;
    private final RestClient restClient;

    public DeepSeekClient(DeepSeekProperties properties,
                          @Qualifier("deepSeekRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** 调用 /chat/completions 获取非流式补全结果。 */
    public DeepSeekChatResponse chatCompletion(DeepSeekChatRequest request) {
        ensureAvailable();
        return restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("DeepSeek API 调用失败: HTTP {} {}", res.getStatusCode(), readBody(res));
                    throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                            "DeepSeek API 调用失败 (HTTP " + res.getStatusCode().value() + ")");
                })
                .body(DeepSeekChatResponse.class);
    }

    /** 启用/密钥校验（Spring AI ChatClient 调用前同样复用，避免双源判断）。 */
    public void ensureAvailable() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "DeepSeek AI 服务未启用，请配置 DEEPSEEK_ENABLED=true");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "DeepSeek API Key 未配置，请在根目录 .env 设置 DEEPSEEK_API_KEY");
        }
    }

    private String readBody(ClientHttpResponse response) {
        try {
            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            return body.length() > 500 ? body.substring(0, 500) + "..." : body;
        } catch (Exception e) {
            return "<unreadable>";
        }
    }
}
