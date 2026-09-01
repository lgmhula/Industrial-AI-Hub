package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * McpInspectionSession 可观测指标单元测试（ADR 0030）。
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class McpInspectionSessionTest {

    @Mock
    private McpSyncClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordToolCall_shouldCountUniqueDevicesAndAlarms() throws Exception {
        McpInspectionSession session = McpInspectionSession.create(client, List.of(), objectMapper);

        session.recordToolCall("mcp_get_device_basic", Map.of("deviceId", 1), "{}");
        session.recordToolCall("mcp_list_device_recent_data", Map.of("deviceId", "1"), "{}");
        session.recordToolCall("mcp_list_device_recent_alarms", Map.of("deviceId", 2),
                "{\"count\":3,\"alarms\":[]}");
        session.recordToolCall("mcp_list_device_recent_alarms", Map.of("deviceId", 2),
                "{\"alarms\":[{},{}]}");

        assertEquals(2, session.deviceCount());
        assertEquals(5, session.alarmCount());
    }

    @Test
    void close_shouldCloseUnderlyingClient() throws Exception {
        McpInspectionSession session = McpInspectionSession.create(client, List.of(), objectMapper);

        session.close();

        verify(client).close();
    }
}
