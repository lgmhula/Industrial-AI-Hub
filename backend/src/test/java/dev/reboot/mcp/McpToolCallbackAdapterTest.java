package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP 客户端工具适配器单元测试（ADR 0030）。
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class McpToolCallbackAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private McpSyncClient client;

    @Mock
    private McpInspectionSession session;

    @Test
    void toolDefinition_shouldCarryMcpToolNameDescriptionAndSchema() {
        McpToolCallbackAdapter adapter = new McpToolCallbackAdapter(
                client, tool(), objectMapper, session);

        ToolDefinition definition = adapter.getToolDefinition();

        assertEquals("mcp_list_devices", definition.name());
        assertEquals("列出设备", definition.description());
        assertTrue(definition.inputSchema().contains("\"limit\""));
    }

    @Test
    void call_shouldForwardArgumentsAndRecordSessionMetrics() {
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("{\"count\":1,\"devices\":[]}")), false));
        McpToolCallbackAdapter adapter = new McpToolCallbackAdapter(
                client, tool(), objectMapper, session);

        String result = adapter.call("{\"limit\":5}");

        assertEquals("{\"count\":1,\"devices\":[]}", result);
        verify(client).callTool(org.mockito.ArgumentMatchers.argThat(request ->
                "mcp_list_devices".equals(request.name())
                        && 5 == ((Number) request.arguments().get("limit")).intValue()));
        verify(session).recordToolCall(eq("mcp_list_devices"), any(), eq("{\"count\":1,\"devices\":[]}"));
    }

    @Test
    void call_errorResult_shouldReturnErrorJson() {
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("设备不存在")), true));
        McpToolCallbackAdapter adapter = new McpToolCallbackAdapter(
                client, tool(), objectMapper, session);

        String result = adapter.call("{}");

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("设备不存在"));
    }

    @Test
    void call_exception_shouldReturnErrorJson() {
        when(client.callTool(any())).thenThrow(new RuntimeException("连接断开"));
        McpToolCallbackAdapter adapter = new McpToolCallbackAdapter(
                client, tool(), objectMapper, session);

        String result = adapter.call("{}");

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("连接断开"));
    }

    private McpSchema.Tool tool() {
        return new McpSchema.Tool("mcp_list_devices", "列出设备",
                new McpSchema.JsonSchema("object",
                        Map.of("limit", Map.of("type", "integer")),
                        List.of(), false, Map.of(), Map.of()));
    }
}
