package dev.reboot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import dev.reboot.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AI 专属限流拦截器（Day 89 Phase 4 AI 模块重构）。
 *
 * <h3>为什么独立于通用 RateLimitInterceptor</h3>
 * <p>AI 接口（{@code /api/ai/**}、{@code /api/agents/**}、{@code /api/rag/**}、{@code /api/mcp/smoke}）
 * 调用 DeepSeek / MCP 通道，有 token 成本 & DeepSeek QPS 限制（DeepSeek 免费版 10 QPM），
 * 跟设备 CRUD 等本地接口完全不在一个成本等级。通用 RateLimitInterceptor 是 50 req/s 的"洪水防护"级别，
 * 保护不了 AI 接口被刷：50 req/s × 模型调用 ≈ 500$ 账单 30 秒。</p>
 *
 * <h3>粒度（由紧到松）</h3>
 * <ol>
 *     <li>已登录用户：按 JWT {@code userId} 每用户独立令牌桶，<b>不共享</b>（防止 VIEWER 批量拉 100 账号 每账号 1QPS 并行爆 DeepSeek）；</li>
 *     <li>未登录用户（匿名）：按请求 IP 桶（匿名通常是 503 未启用路径，但限流仍生效，防止脚本探活 /api/ai 打满连接）；</li>
 *     <li>ADMIN 角色：令牌桶 {@code rate.limit.ai.adminPermits}，默认 5/s（比普通用户宽，允许 ADMIN 巡检/批量摘要）。</li>
 * </ol>
 *
 * <h3>配置（通过 application-*.yml 或 .env 注入）</h3>
 * <pre>{@code
 * rate:
 *   limit:
 *     ai.permits: 2            # 普通 VIEWER/OPERATOR: 2 req/s
 *     ai.adminPermits: 5      # ADMIN: 5 req/s
 *     ai.acquireTimeoutMs: 150 # 最多等待 150 ms 拿不到即拒绝（不阻塞主响应，429 快速返回）
 * }</pre>
 *
 * <h3>错误响应</h3>
 * {@code HTTP 429} + JSON body {@code {"code":429,"message":"AI 接口调用过于频繁，请稍后重试","data":null}}，
 * 与通用 RateLimitInterceptor body 结构一致但 message 明说"AI 接口"，前端区分错误原因。
 *
 * @author AI 助手 + hula0710
 * @since Day 89（Phase 4 AI 模块重构，独立 AI 限流）
 */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitInterceptor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ANONYMOUS_PREFIX = "ip:";
    private static final String USER_PREFIX = "u:";
    private static final String ADMIN_ROLE_NAME = "ADMIN";

    /** 每个 user/ip 的令牌桶缓存；LRU 未来可替换为 Caffeine，当前 ConcurrentHashMap 足够（<10k 用户）。 */
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final double defaultPermits;
    private final double adminPermits;
    private final long acquireTimeoutMs;

    public AiRateLimitInterceptor(
            @Value("${rate.limit.ai.permits:2}") double defaultPermits,
            @Value("${rate.limit.ai.adminPermits:5}") double adminPermits,
            @Value("${rate.limit.ai.acquireTimeoutMs:150}") long acquireTimeoutMs) {
        this.defaultPermits = positive(defaultPermits, "rate.limit.ai.permits", 2.0);
        this.adminPermits = positive(adminPermits, "rate.limit.ai.adminPermits", 5.0);
        this.acquireTimeoutMs = acquireTimeoutMs > 0 ? acquireTimeoutMs : 150;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) request.getAttribute("roles");
        String bucketKey = bucketKey(userId, request);
        double rate = isAdmin(roles) ? adminPermits : defaultPermits;

        RateLimiter limiter = limiters.computeIfAbsent(bucketKey, k -> RateLimiter.create(rate));
        // 热更新：用户升级到 ADMIN 后（桶已存在按旧 rate），显式同步
        if (Math.abs(limiter.getRate() - rate) > 0.001) {
            limiter.setRate(rate);
        }
        if (limiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
            return true;
        }
        log.warn("AI 限流触发 key={} path={} rate={}/s admin={} userId={} ip={}",
                bucketKey, request.getRequestURI(), rate, isAdmin(roles), userId, clientIp(request));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(
                ApiResponse.error(429, "AI 接口调用过于频繁，请稍后重试")));
        return false;
    }

    // ===================== 私有辅助 =====================

    private static String bucketKey(@Nullable Long userId, HttpServletRequest request) {
        if (userId != null) {
            return USER_PREFIX + userId;
        }
        return ANONYMOUS_PREFIX + clientIp(request);
    }

    private static boolean isAdmin(@Nullable java.util.List<String> roles) {
        if (roles == null || roles.isEmpty()) return false;
        for (String r : roles) {
            if (ADMIN_ROLE_NAME.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    /** 常见反向代理链下真实客户端 IP；回退到 remoteAddr。 */
    private static String clientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
        };
        for (String h : headers) {
            String v = request.getHeader(h);
            if (v == null || v.isBlank() || "unknown".equalsIgnoreCase(v)) continue;
            int comma = v.indexOf(',');
            return (comma > 0 ? v.substring(0, comma) : v).trim();
        }
        return request.getRemoteAddr();
    }

    private static double positive(double v, String name, double fallback) {
        if (v > 0) return v;
        log.warn("{} 配置值 {} 非法（必须>0），回退到默认 {}", name, v, fallback);
        return fallback;
    }
}
