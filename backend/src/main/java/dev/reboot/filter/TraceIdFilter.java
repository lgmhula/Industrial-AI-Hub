package dev.reboot.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 Filter（Day 101）— 为每个 HTTP 请求生成/透传 traceId 并写入 MDC。
 *
 * <p>日志 pattern 通过 {@code %X{traceId:-}} 输出，一次请求内所有日志行可串联；
 * 响应头 {@code X-Trace-Id} 回传，前端/压测工具（Day 102 JMeter）可凭此关联后端日志。</p>
 *
 * <p>规则：
 * <ul>
 *   <li>请求头携带合法 {@code X-Trace-Id}（非空且 ≤32 字符）→ 透传复用（网关/联调场景）；</li>
 *   <li>否则生成 16 位短 UUID（无连字符）——日志单行宽度友好；</li>
 *   <li>finally 中 {@link MDC#remove}，防止容器线程池复用导致 traceId 串号。</li>
 * </ul></p>
 *
 * <p>注册于 {@code WebMvcConfig}（order = -2，最外层，先于 MCP 0 / JWT 1）。</p>
 *
 * @author hula0710
 * @since 2026-10-13
 */
@Component
public class TraceIdFilter implements Filter {

    /** 响应头与请求头共用的 traceId 头名。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC key（logback pattern 中 {@code %X{traceId:-}} 引用）。 */
    public static final String MDC_TRACE_ID_KEY = "traceId";

    /** 透传 traceId 最大长度防御：超长视为滥用，重新生成。 */
    static final int MAX_TRACE_ID_LENGTH = 32;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String traceId = resolveTraceId(req.getHeader(TRACE_ID_HEADER));
        MDC.put(MDC_TRACE_ID_KEY, traceId);
        res.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }

    /**
     * 解析 traceId：合法透传头值优先，否则生成 16 位短 UUID。
     */
    private String resolveTraceId(String incoming) {
        if (incoming != null && !incoming.isBlank() && incoming.length() <= MAX_TRACE_ID_LENGTH) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
