package dev.reboot.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具暴露边界（ADR 0027）。
 *
 * <p>Spring AI MCP Server 自动配置会收集容器内所有 {@code ToolCallbackProvider} /
 * {@code ToolCallback} Bean 注册为 MCP tools。为避免把内部 Agent 工具
 * （{@code DeviceAiTools}，依赖 ToolContext.userId 做站点授权）意外暴露给无法携带
 * 用户身份的 MCP 客户端，这里只显式注册 {@link McpDeviceTools} 的只读工具。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Configuration
public class McpToolConfig {

    @Bean
    ToolCallbackProvider mcpDeviceToolCallbackProvider(McpDeviceTools mcpDeviceTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpDeviceTools)
                .build();
    }
}
