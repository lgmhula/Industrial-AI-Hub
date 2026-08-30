package dev.reboot.mcp;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.RoleEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 客户端验证入口（Day 82，ADR 0029）。
 *
 * <p>{@code POST /api/mcp/smoke} 走标准 JWT + ADMIN 权限（与现有 REST 分层一致），
 * 内部再以 MCP 客户端身份连接 SSE Server，验证握手、工具清单与只读探针。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@RestController
@RequestMapping("/api/mcp")
@Tag(name = "13-MCP 客户端", description = "MCP Server 连接验证与工具清单冒烟")
public class McpController {

    private final McpClientService mcpClientService;

    public McpController(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @PostMapping("/smoke")
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "MCP 连接冒烟（握手 + 工具清单 + 只读探针）")
    public ApiResponse<McpSmokeResult> smoke() {
        return ApiResponse.ok("MCP 冒烟通过", mcpClientService.smoke());
    }
}
