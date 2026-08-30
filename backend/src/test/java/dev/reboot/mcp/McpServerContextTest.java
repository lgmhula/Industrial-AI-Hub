package dev.reboot.mcp;

import dev.reboot.service.CacheService;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Server 上下文验证（ADR 0027）。
 *
 * <p>测试 Profile 默认排除 MCP 自动配置，本测试通过
 * {@code spring.autoconfigure.exclude=} 清空排除清单，单独验证：
 * Spring AI 能创建 {@link McpSyncServer}，且 {@link McpDeviceTools} 的
 * 7 个只读工具被注册为 MCP tools。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@SpringBootTest
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
class McpServerContextTest {

    @MockBean
    private CacheService cacheService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void mcpServerAutoConfigurationShouldCreateSyncServer() {
        assertFalse(applicationContext.getBeansOfType(McpSyncServer.class).isEmpty(),
                "MCP Server 自动配置应创建 McpSyncServer");
    }

    @Test
    void mcpDeviceToolsShouldBeRegisteredAsToolCallbacks() {
        ToolCallbackProvider provider = applicationContext.getBean(ToolCallbackProvider.class);
        String[] toolNames = Arrays.stream(provider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name())
                .toArray(String[]::new);

        assertTrue(Arrays.asList(toolNames).containsAll(
                Arrays.asList("mcp_list_devices",
                        "mcp_get_device_basic",
                        "mcp_list_device_recent_data",
                        "mcp_list_device_recent_alarms",
                        "mcp_get_device_data_range",
                        "mcp_get_device_data_stats",
                        "mcp_search_devices")),
                "7 个 MCP 只读工具应全部注册");
    }
}
