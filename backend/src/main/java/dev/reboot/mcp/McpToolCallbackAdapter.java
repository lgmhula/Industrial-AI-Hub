package dev.reboot.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 客户端工具到 Spring AI {@link ToolCallback} 的适配器（Week 12 Day 83，ADR 0030）。
 *
 * <p>工具名/描述/输入 Schema 直接取自 MCP Server 的工具清单，调用时把模型生成的
 * JSON 参数转发到 {@link McpSyncClient#callTool}。与内部 Agent 工具一致，业务失败
 * 返回 {@code {"error":"..."}} JSON 而非抛异常，让 Agent 可以继续收尾生成日报。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
public class McpToolCallbackAdapter implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(McpToolCallbackAdapter.class);

    private final McpSyncClient client;
    private final McpSchema.Tool tool;
    private final ObjectMapper objectMapper;
    private final McpInspectionSession session;

    public McpToolCallbackAdapter(McpSyncClient client,
                                  McpSchema.Tool tool,
                                  ObjectMapper objectMapper,
                                  McpInspectionSession session) {
        this.client = client;
        this.tool = tool;
        this.objectMapper = objectMapper;
        this.session = session;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        try {
            return DefaultToolDefinition.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .inputSchema(objectMapper.writeValueAsString(tool.inputSchema()))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("MCP 工具 Schema 序列化失败: " + tool.name(), e);
        }
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        Map<String, Object> arguments = parseArguments(toolInput);
        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(tool.name(), arguments));
            String text = extractText(result);
            if (Boolean.TRUE.equals(result.isError())) {
                log.error("MCP 工具调用失败: {} {}", tool.name(), text);
                return errorJson(text == null ? "MCP 工具调用失败" : text);
            }
            session.recordToolCall(tool.name(), arguments, text);
            return text;
        } catch (Exception e) {
            log.error("MCP 工具调用异常: {} {}", tool.name(), e.getMessage());
            return errorJson("MCP 工具调用异常: " + e.getMessage());
        }
    }

    private Map<String, Object> parseArguments(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("MCP 工具参数 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String extractText(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent && textContent.text() != null) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(textContent.text());
            }
        }
        if (!text.isEmpty()) {
            return text.toString();
        }
        try {
            return objectMapper.writeValueAsString(result.content());
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"MCP 工具调用失败\"}";
        }
    }
}
