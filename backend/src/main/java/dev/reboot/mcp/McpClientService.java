package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端集成（Day 82，ADR 0029）。
 *
 * <p>用 MCP 1.0 Java SDK 的 {@link HttpClientSseClientTransport} 连接本服务暴露的
 * SSE MCP Server，完成三步冒烟：initialize 握手 → listTools 工具清单 → 调用
 * {@code mcp_list_devices}（limit=1）做真实链路探针。所有工具均为只读，
 * 不触发任何写操作。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private static final String CLIENT_NAME = "industrial-ai-hub-mcp-client";
    private static final String CLIENT_VERSION = "1.0.0";

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String sseEndpoint;
    private final String accessToken;

    public McpClientService(ObjectMapper objectMapper,
                            @Value("${app.mcp.client.base-url:http://localhost:8080}") String baseUrl,
                            @Value("${app.mcp.client.sse-endpoint:/mcp/sse}") String sseEndpoint,
                            @Value("${app.mcp.access-token:}") String accessToken) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.sseEndpoint = sseEndpoint;
        this.accessToken = accessToken;
    }

    /** 连接 MCP Server 并返回握手、工具清单与只读探针结果。 */
    public McpSmokeResult smoke() {
        HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .objectMapper(objectMapper);
        if (StringUtils.hasText(accessToken)) {
            String token = accessToken;
            transportBuilder.customizeRequest(rb -> rb.header(McpAccessFilter.HEADER, token));
        }

        try (McpSyncClient client = McpClient.sync(transportBuilder.build())
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            List<String> toolNames = tools.tools().stream()
                    .map(McpSchema.Tool::name)
                    .sorted()
                    .toList();
            String probe = probeListDevices(client);
            return new McpSmokeResult(
                    client.getServerInfo().name(),
                    client.getServerInfo().version(),
                    client.getServerInstructions(),
                    toolNames,
                    "mcp_list_devices",
                    probe,
                    toolNames.size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP 冒烟连接失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "MCP Server 连接失败: " + e.getMessage());
        }
    }

    private String probeListDevices(McpSyncClient client) {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("mcp_list_devices", Map.of("limit", 1)));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "MCP 探针工具调用失败");
        }
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent tc) {
                text.append(tc.text());
            }
        }
        return text.toString();
    }
}
