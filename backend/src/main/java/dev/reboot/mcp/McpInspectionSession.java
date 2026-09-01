package dev.reboot.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 MCP 巡检会话（Week 12 Day 83，ADR 0030）。
 *
 * <p>一个巡检流程只建立一次 SSE 连接，会话内复用同一个 {@link McpSyncClient}
 * 供 Agent 多次调用只读 MCP 工具；关闭会话时统一释放连接。会话同时记录轻量
 * 可观测指标：去重设备数、最近告警条数，供日报结果与审计使用。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
public class McpInspectionSession implements AutoCloseable {

    private static final Set<String> DEVICE_TOOLS = Set.of(
            "mcp_get_device_basic",
            "mcp_list_device_recent_data",
            "mcp_list_device_recent_alarms",
            "mcp_get_device_data_range",
            "mcp_get_device_data_stats");

    private final McpSyncClient client;
    private final ObjectMapper objectMapper;
    private final Set<Long> inspectedDeviceIds = new LinkedHashSet<>();
    private final AtomicInteger alarmCount = new AtomicInteger();

    private ToolCallback[] toolCallbacks = new ToolCallback[0];

    private McpInspectionSession(McpSyncClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /** 由 {@link McpClientService} 创建会话并把 MCP 工具清单适配为 Agent 可调用回调。 */
    static McpInspectionSession create(McpSyncClient client,
                                       List<McpSchema.Tool> tools,
                                       ObjectMapper objectMapper) {
        McpInspectionSession session = new McpInspectionSession(client, objectMapper);
        session.toolCallbacks = tools.stream()
                .map(tool -> (ToolCallback) new McpToolCallbackAdapter(client, tool, objectMapper, session))
                .toArray(ToolCallback[]::new);
        return session;
    }

    public ToolCallback[] toolCallbacks() {
        return toolCallbacks;
    }

    public int deviceCount() {
        return inspectedDeviceIds.size();
    }

    public int alarmCount() {
        return alarmCount.get();
    }

    /** 工具调用后记录可观测指标：设备 ID 去重、最近告警条数累计。 */
    public void recordToolCall(String toolName, Map<String, Object> arguments, String resultText) {
        if (toolName == null) {
            return;
        }
        if (DEVICE_TOOLS.contains(toolName)) {
            Long deviceId = longArg(arguments == null ? null : arguments.get("deviceId"));
            if (deviceId != null) {
                inspectedDeviceIds.add(deviceId);
            }
        }
        if ("mcp_list_device_recent_alarms".equals(toolName) && StringUtils.hasText(resultText)) {
            alarmCount.addAndGet(countAlarms(resultText));
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    private Long longArg(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && text.matches("-?\\d+")) {
            return Long.valueOf(text);
        }
        return null;
    }

    private int countAlarms(String resultText) {
        try {
            JsonNode node = objectMapper.readTree(resultText);
            JsonNode count = node.get("count");
            if (count != null && count.isNumber()) {
                return count.asInt();
            }
            JsonNode alarms = node.get("alarms");
            if (alarms != null && alarms.isArray()) {
                return alarms.size();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
