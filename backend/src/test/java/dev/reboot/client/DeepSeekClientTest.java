package dev.reboot.client;

import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.DeepSeekChatRequest;
import dev.reboot.dto.ai.DeepSeekChatResponse;
import dev.reboot.dto.ai.DeepSeekMessage;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DeepSeekClient HTTP 层单元测试（MockRestServiceServer 模拟上游）。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
class DeepSeekClientTest {

    private DeepSeekProperties properties;
    private DeepSeekClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.deepseek.com");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new DeepSeekClient(properties, restClient);
    }

    @Test
    void chatCompletion_shouldSendAuthHeaderAndParseUsage() {
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-1",
                          "object": "chat.completion",
                          "model": "deepseek-chat",
                          "choices": [
                            {
                              "index": 0,
                              "message": {"role": "assistant", "content": "你好"},
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {"prompt_tokens": 12, "completion_tokens": 5, "total_tokens": 17}
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekChatRequest request = new DeepSeekChatRequest();
        request.setModel("deepseek-chat");
        request.setMessages(List.of(DeepSeekMessage.user("你好")));
        request.setMaxTokens(128);
        request.setTemperature(0.3);

        DeepSeekChatResponse response = client.chatCompletion(request);

        assertEquals("deepseek-chat", response.getModel());
        assertEquals("你好", response.getChoices().get(0).getMessage().getContent());
        assertEquals(17, response.getUsage().getTotalTokens());
        server.verify();
    }

    @Test
    void chatCompletion_disabled_shouldThrowServiceUnavailable() {
        properties.setEnabled(false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> client.chatCompletion(new DeepSeekChatRequest()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void chatCompletion_missingApiKey_shouldThrowServiceUnavailable() {
        properties.setApiKey(" ");
        BusinessException e = assertThrows(BusinessException.class,
                () -> client.chatCompletion(new DeepSeekChatRequest()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void chatCompletion_upstreamError_shouldThrowServiceUnavailable() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"invalid api key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        BusinessException e = assertThrows(BusinessException.class,
                () -> client.chatCompletion(new DeepSeekChatRequest()));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }
}
