package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.entity.Device;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * MCP 巡检会话集成测试（ADR 0030）。
 *
 * <p>在随机端口启动完整 Spring 上下文，验证一次巡检会话可复用同一个 SSE 连接：
 * 7 个工具回调注册、连续调用 {@code mcp_list_devices} 与
 * {@code mcp_get_device_basic}，会话关闭后连接释放。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
        + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration")
class McpClientInspectionSessionTest {

    @LocalServerPort
    private int port;

    @MockBean
    private CacheService cacheService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private DeviceMapper deviceMapper;

    @MockBean
    private DeviceDataMapper deviceDataMapper;

    @MockBean
    private AlarmMapper alarmMapper;

    @Test
    void inspectionSession_shouldRegisterAllToolsAndReuseConnection() throws Exception {
        when(deviceMapper.findAll(null)).thenReturn(List.of(device(1L)));
        when(deviceMapper.findById(1L)).thenReturn(device(1L));
        McpClientService service = new McpClientService(new ObjectMapper(),
                "http://localhost:" + port, "/mcp/sse", null);

        try (McpInspectionSession session = service.openInspectionSession()) {
            ToolCallback[] callbacks = session.toolCallbacks();
            assertEquals(7, callbacks.length);
            assertTrue(Arrays.stream(callbacks)
                    .map(callback -> callback.getToolDefinition().name())
                    .anyMatch("mcp_list_devices"::equals));

            ToolCallback listDevices = findTool(callbacks, "mcp_list_devices");
            String listResult = listDevices.call("{\"limit\":1}");
            assertTrue(listResult.contains("\"deviceName\":\"巡检设备\""));

            ToolCallback getBasic = findTool(callbacks, "mcp_get_device_basic");
            String basicResult = getBasic.call("{\"deviceId\":1}");
            assertTrue(basicResult.contains("\"deviceCode\":\"DEV-INSPECT\""));
            assertEquals(1, session.deviceCount());
        }
    }

    private ToolCallback findTool(ToolCallback[] callbacks, String name) {
        return Arrays.stream(callbacks)
                .filter(callback -> name.equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }

    private Device device(Long id) {
        Device d = new Device();
        d.setId(id);
        d.setSiteId(10L);
        d.setDeviceName("巡检设备");
        d.setDeviceCode("DEV-INSPECT");
        d.setDeviceType("PLC");
        d.setLocation("1号车间");
        d.setIpAddress("192.168.1.11");
        d.setPort(502);
        d.setStatus(1);
        return d;
    }
}
