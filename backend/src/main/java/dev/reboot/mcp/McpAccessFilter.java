package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.dto.ApiResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MCP 传输层访问令牌过滤器（Day 82，ADR 0029）。
 *
 * <p>MCP 1.0 SSE 端点本身没有 HTTP 鉴权语义，Spring AI MCP Server 也不会透传 Header
 * 到工具上下文。为了让内网可信通道有一个显式的传输边界，这里提供一个可选的共享令牌门：
 * 当 {@code app.mcp.access-token} 配置非空时，{@code /mcp/sse} 与 {@code /mcp/message}
 * 必须携带一致的自定义头 {@code X-MCP-Token}；未配置（本地开发默认）则放行。</p>
 *
 * <p>RBAC 边界：MCP 工具保持只读且无用户身份，管理/写操作仍走 JWT REST 接口；
 * 未来如需按用户隔离，再在 MCP 1.1 OAuth / 身份头协议落地后演进。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@Component
public class McpAccessFilter implements Filter {

    public static final String HEADER = "X-MCP-Token";

    private static final Logger log = LoggerFactory.getLogger(McpAccessFilter.class);

    private final String accessToken;
    private final ObjectMapper objectMapper;

    public McpAccessFilter(@Value("${app.mcp.access-token:}") String accessToken,
                           ObjectMapper objectMapper) {
        this.accessToken = accessToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (!StringUtils.hasText(accessToken)) {
            chain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER);
        if (token == null || !MessageDigest.isEqual(
                accessToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("MCP 访问令牌校验失败: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(401, "MCP 访问令牌无效")));
            return;
        }

        chain.doFilter(request, response);
    }
}
