package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * MCP 客户端冒烟集成测试（ADR 0029）。
 *
 * <p>在随机端口启动完整 Spring 上下文（含 MCP Server 自动配置），用 MCP SDK 的
 * SSE 客户端连接本服务，验证握手、7 个工具清单与只读探针调用。DB Mapper 全部 mock，
 * 探针调用不落库。</p>
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
class McpClientSmokeTest {

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
    void smoke_shouldConnectAndListAllSevenTools() {
        when(deviceMapper.findAll(null)).thenReturn(List.of(device(1L)));
        McpClientService service = new McpClientService(new ObjectMapper(),
                "http://localhost:" + port, "/mcp/sse", null);

        McpSmokeResult result = service.smoke();

        assertEquals("industrial-ai-hub-mcp", result.serverName());
        assertEquals(7, result.toolCount());
        assertTrue(result.toolNames().containsAll(List.of(
                "mcp_list_devices",
                "mcp_get_device_basic",
                "mcp_list_device_recent_data",
                "mcp_list_device_recent_alarms",
                "mcp_get_device_data_range",
                "mcp_get_device_data_stats",
                "mcp_search_devices")));
        assertTrue(result.probeResult().contains("\"devices\""));
        assertTrue(result.probeResult().contains("\"deviceName\":\"冒烟设备\""));
    }

    @Test
    void smoke_unreachableServer_shouldFailWithBusinessException() {
        McpClientService service = new McpClientService(new ObjectMapper(),
                "http://localhost:1", "/mcp/sse", null);

        BusinessException e = assertThrows(BusinessException.class, service::smoke);

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    private Device device(Long id) {
        Device d = new Device();
        d.setId(id);
        d.setSiteId(10L);
        d.setDeviceName("冒烟设备");
        d.setDeviceCode("DEV-SMOKE");
        d.setDeviceType("PLC");
        d.setLocation("1号车间");
        d.setIpAddress("192.168.1.10");
        d.setPort(502);
        d.setStatus(1);
        return d;
    }
}
