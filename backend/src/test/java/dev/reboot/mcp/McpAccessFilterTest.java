package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpAccessFilter 单元测试（ADR 0029）。
 *
 * @author AI 助手
 * @since 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class McpAccessFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tokenNotConfigured_shouldPassThrough() throws Exception {
        McpAccessFilter filter = new McpAccessFilter("", objectMapper);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void matchingToken_shouldPassThrough() throws Exception {
        McpAccessFilter filter = new McpAccessFilter("secret-token", objectMapper);
        when(request.getHeader(McpAccessFilter.HEADER)).thenReturn("secret-token");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void missingToken_shouldRejectWith401() throws Exception {
        McpAccessFilter filter = new McpAccessFilter("secret-token", objectMapper);
        when(request.getHeader(McpAccessFilter.HEADER)).thenReturn(null);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void wrongToken_shouldRejectWith401() throws Exception {
        McpAccessFilter filter = new McpAccessFilter("secret-token", objectMapper);
        when(request.getHeader(McpAccessFilter.HEADER)).thenReturn("wrong-token");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
