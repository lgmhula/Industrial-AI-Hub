package dev.reboot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import dev.reboot.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流拦截器 —— Guava RateLimiter 简单实现。
 *
 * <p>按 URI 路径独立限流，避免高频接口互相影响。</p>
 * <p>限流值通过 system properties 可配，默认 50 req/s。</p>
 *
 * @author hula0710
 * @since 2026-07-30
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final double defaultPermits;

    public RateLimitInterceptor() {
        this.defaultPermits = Double.parseDouble(
                System.getProperty("rate.limit.permits", "50"));
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        String path = uri.replaceAll("\\d+", "{id}"); // 参数化路径，共用同一令牌桶

        RateLimiter limiter = limiters.computeIfAbsent(path,
                k -> RateLimiter.create(defaultPermits));

        if (!limiter.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            log.warn("Rate limit exceeded: {} (permits={})", path, defaultPermits);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(mapper.writeValueAsString(
                    ApiResponse.error(429, "请求过于频繁，请稍后再试")));
            return false;
        }
        return true;
    }
}
