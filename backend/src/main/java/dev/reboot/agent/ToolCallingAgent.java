package dev.reboot.agent;

import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.AiToolCallTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用 ReAct 工具调用 Agent 循环（Week 12 Day 79，ADR 0026）。
 *
 * <p>显式手动循环：模型请求工具 → 执行 → 结果回填对话 → 再次调用。每轮关闭
 * Spring AI 自动工具循环，精确控制轮次与调用数。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class ToolCallingAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolCallingAgent.class);

    private final ChatModel chatModel;
    private final DeepSeekProperties properties;

    public ToolCallingAgent(ChatModel chatModel, DeepSeekProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    public AgentRunResult run(String systemPrompt,
                              String userPrompt,
                              ToolContext toolContext,
                              ToolCallback[] toolCallbacks,
                              int maxRounds) {
        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        conversation.add(new UserMessage(userPrompt));

        int rounds = 0;
        int toolCallCount = 0;
        List<AiToolCallTrace> trace = new ArrayList<>();

        while (true) {
            ChatResponse response = chatModel.call(
                    new Prompt(conversation, toolOptions(toolContext.getContext(), toolCallbacks, true)));
            if (!response.hasToolCalls()) {
                return new AgentRunResult(extractText(response), rounds, toolCallCount,
                        rounds > 0, false, trace);
            }
            if (rounds >= maxRounds) {
                log.warn("Agent 工具调用达到 {} 轮硬限，强制收尾", maxRounds);
                return new AgentRunResult(forceFinalize(conversation), rounds, toolCallCount,
                        rounds > 0, true, trace);
            }

            rounds++;
            AssistantMessage assistant = response.getResult() == null ? null : response.getResult().getOutput();
            if (assistant == null) {
                return new AgentRunResult("", rounds, toolCallCount, rounds > 0, false, trace);
            }

            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                toolCallCount++;
                ToolCallback callback = findTool(toolCall.name(), toolCallbacks);
                if (callback == null) {
                    log.warn("AI 请求未知工具: {}", toolCall.name());
                    trace.add(new AiToolCallTrace(toolCall.name(), false));
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolCall.name(), "{\"error\":\"未知工具: " + toolCall.name() + "\"}"));
                    continue;
                }
                String result = callback.call(toolCall.arguments(), toolContext);
                trace.add(new AiToolCallTrace(toolCall.name(), isSuccess(result)));
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), result));
            }

            List<Message> history = new ArrayList<>(conversation);
            history.add(assistant);
            history.add(new ToolResponseMessage(toolResponses));
            conversation = history;
        }
    }

    private String forceFinalize(List<Message> conversation) {
        List<Message> history = new ArrayList<>(conversation);
        history.add(new UserMessage("已达到工具调用轮次上限。请仅基于以上已获取的数据给出最终答案，不要再次请求调用工具。"));
        ChatResponse response = chatModel.call(new Prompt(history, toolOptions(Map.of(), new ToolCallback[0], false)));
        return extractText(response);
    }

    private OpenAiChatOptions toolOptions(Map<String, Object> context,
                                          ToolCallback[] toolCallbacks,
                                          boolean withTools) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build())
                .internalToolExecutionEnabled(false)
                .toolContext(context);
        if (withTools) {
            builder.toolCallbacks(toolCallbacks);
        }
        return builder.build();
    }

    private ToolCallback findTool(String name, ToolCallback[] toolCallbacks) {
        if (name == null) {
            return null;
        }
        for (ToolCallback callback : toolCallbacks) {
            if (name.equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
    }

    private boolean isSuccess(String result) {
        return result != null && !result.contains("\"error\"");
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
