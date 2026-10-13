package dev.reboot.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceIdFilter 单元测试（Day 101）。
 *
 * <p>覆盖：无头生成 + 响应头回传 + 链后 MDC 清理、合法头透传复用、
 * 超长头防御性重新生成、MDC 值可被链内读取。</p>
 *
 * @author AI 助手
 * @since 2026-10-13
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    /** 无请求头 → 生成 16 位 traceId，响应头回传，链执行后 MDC 清理（防线程复用串号）。 */
    @Test
    void shouldGenerateTraceIdAndClearMdcAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String traceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        assertTrue(traceId.matches("[0-9a-f]{16}"));
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY), "chain 返回后 MDC 必须被清理");
    }

    /** 合法 X-Trace-Id 头（≤32 字符）→ 透传复用，不重新生成；链内 MDC 可读取该值。 */
    @Test
    void shouldReuseIncomingTraceIdInsideChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "jmeter-run-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain[] captured = new MockFilterChain[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, ServletException {
                captured[0] = this;
                // 链内（模拟 Controller）视角：MDC 已注入
                org.junit.jupiter.api.Assertions.assertEquals(
                        "jmeter-run-42", MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals("jmeter-run-42", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNotNull(captured[0], "链内逻辑必须被执行");
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY));
    }

    /** 超长头（>32 字符）→ 视为滥用，重新生成而非透传。 */
    @Test
    void shouldRegenerateWhenIncomingHeaderTooLong() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "x".repeat(33));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String traceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        assertNotEquals("x".repeat(33), traceId);
    }
}
