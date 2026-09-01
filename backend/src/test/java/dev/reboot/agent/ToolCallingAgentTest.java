package dev.reboot.agent;

import dev.reboot.config.DeepSeekProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ToolCallingAgent 单元测试 —— 通用手动工具循环。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ExtendWith(MockitoExtension.class)
class ToolCallingAgentTest {

    @Mock private ChatModel chatModel;

    private ToolCallingAgent agent;

    @BeforeEach
    void setUp() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setModel("deepseek-chat");
        properties.setMaxTokens(1024);
        properties.setTemperature(0.3);
        agent = new ToolCallingAgent(chatModel, properties);
    }

    @Test
    void run_withoutToolCalls_shouldReturnTextAndNotReferencedRealTime() {
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("设备运行正常。"));

        AgentRunResult result = agent.run("系统", "问题", new ToolContext(Map.of()),
                new ToolCallback[0], 3);

        assertEquals("设备运行正常。", result.answer());
        assertEquals(0, result.toolRounds());
        assertEquals(0, result.toolCalls());
        assertFalse(result.referencedRealTime());
        assertFalse(result.truncated());
    }

    @Test
    void run_shouldExecuteToolAndFeedResultBack() {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("get_device_basic");
        when(callback.call(eq("{}"), any(ToolContext.class))).thenReturn("{\"deviceName\":\"测试设备\"}");
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("call_1", "get_device_basic", "{}"))
                .thenReturn(textResponse("设备在线，运行正常。"));

        AgentRunResult result = agent.run("系统", "问题", new ToolContext(Map.of()),
                new ToolCallback[]{callback}, 3);

        assertEquals("设备在线，运行正常。", result.answer());
        assertEquals(1, result.toolRounds());
        assertEquals(1, result.toolCalls());
        assertTrue(result.referencedRealTime());
        assertEquals("get_device_basic", result.toolTrace().get(0).getToolName());
        assertTrue(result.toolTrace().get(0).isSuccess());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(2)).call(captor.capture());
        List<Message> secondMessages = captor.getAllValues().get(1).getInstructions();
        assertEquals(4, secondMessages.size());
        assertTrue(secondMessages.get(3) instanceof ToolResponseMessage);
    }

    @Test
    void run_hitsHardLimit_shouldTruncateWithoutMoreTools() {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("get_device_basic");
        when(callback.call(eq("{}"), any(ToolContext.class))).thenReturn("{}");
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("c1", "get_device_basic", "{}"))
                .thenReturn(toolCallResponse("c2", "get_device_basic", "{}"))
                .thenReturn(textResponse("已基于已有数据总结。"));

        AgentRunResult result = agent.run("系统", "问题", new ToolContext(Map.of()),
                new ToolCallback[]{callback}, 1);

        assertEquals(1, result.toolRounds());
        assertEquals(1, result.toolCalls());
        assertTrue(result.truncated());
        assertEquals("已基于已有数据总结。", result.answer());
    }

    private ChatResponse textResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    private ChatResponse toolCallResponse(String id, String toolName, String arguments) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("", Map.of(),
                        List.of(new AssistantMessage.ToolCall(id, "function", toolName, arguments))))))
                .build();
    }
}
