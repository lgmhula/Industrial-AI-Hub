package dev.reboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DeepSeekConfig 测试 —— Spring AI Bean 默认参数与结构化 JSON 输出。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
class DeepSeekConfigTest {

    @Test
    void springAiBeans_shouldUseDeepSeekDefaultsWithJsonObjectOutput() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        properties.setModel("deepseek-chat");
        properties.setMaxTokens(1024);
        properties.setTemperature(0.3);

        DeepSeekConfig config = new DeepSeekConfig();
        OpenAiApi api = config.openAiApi(properties);
        OpenAiChatModel chatModel = config.openAiChatModel(api, properties);
        ChatClient chatClient = config.chatClient(chatModel);

        assertNotNull(api);
        assertNotNull(chatClient);
        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.getDefaultOptions();
        assertEquals("deepseek-chat", options.getModel());
        assertEquals(0.3, options.getTemperature());
        assertEquals(1024, options.getMaxTokens());
        assertEquals(ResponseFormat.Type.JSON_OBJECT, options.getResponseFormat().getType());
    }
}
