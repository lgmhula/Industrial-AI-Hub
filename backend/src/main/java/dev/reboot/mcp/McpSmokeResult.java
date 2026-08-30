package dev.reboot.mcp;

import java.util.List;

/**
 * MCP 客户端冒烟结果（Day 82，ADR 0029）。
 *
 * @param serverName        MCP Server 名称
 * @param serverVersion     MCP Server 版本
 * @param serverInstructions MCP Server 指令（用于引导客户端）
 * @param toolNames         已注册工具清单（排序后）
 * @param probeTool         探针调用的工具名
 * @param probeResult       探针工具返回的原始 JSON 文本
 * @param toolCount         工具数量
 * @author AI 助手
 * @since 2026-08-30
 */
public record McpSmokeResult(String serverName,
                             String serverVersion,
                             String serverInstructions,
                             List<String> toolNames,
                             String probeTool,
                             String probeResult,
                             int toolCount) {
}
