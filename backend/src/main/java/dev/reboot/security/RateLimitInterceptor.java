package dev.reboot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接口限流拦截器 —— 本地/Redis 双模式令牌桶（Day 37 引入，Day 103 加固）。
 *
 * <p>按 URI 路径建立全局桶，并为登录用户/IP 建立身份桶；两层都通过后才放行。
 * {@code rate.limit.mode=redis} 时两个桶均通过 Lua 在实例间共享。</p>
 *
 * <h3>Day 103 两项调整（依据 Day 102 压测证据）</h3>
 * <ol>
 *   <li><b>拒绝日志节流（修复）</b>：原先每拒绝一次写一行 WARN，实测 30s / 4639 req/s 突发产出
 *       137,568 行 ≈ 23.2MB（约 780KB/s），持续洪泛可直接写满磁盘。现改为
 *       「每个窗口最多一行 + 汇总该窗口抑制条数」（{@code rate.limit.log-interval-ms}，默认 5000ms）。</li>
 *   <li><b>取令牌超时显式可配（不是缺陷修复，是澄清）</b>：{@code rate.limit.acquire-timeout-ms} 默认
 *       {@code 500}（保持 Day 37 以来行为）。按响应码拆分 Day 102 原始采样后可确认：
 *       429 拒绝路径 avg 0.9ms / p99 3ms（**从不阻塞**），而 ~500ms 的等待全部属于**最终被放行**的请求
 *       （200：avg 474ms / p50 506ms）——这是限流器有界背压队列在起作用，
 *       同时占用的线程数上界 ≈ {@code permits × timeout} ≈ 25（远低于 Tomcat 200）。
 *       置 0 可改成"立即甩掉"（延迟可预测、不排队），但会牺牲突发容忍度，属策略取舍，故不改默认值。</li>
 * </ol>
 *
 * <p>限流值通过 {@code rate.limit.permits} 属性可配，默认 50 req/s。</p>
 *
 * @author hula0710
 * @since 2026-07-30
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, PathLogState> logStates = new ConcurrentHashMap<>();
    private final RateLimitStore rateLimitStore;
    private final double defaultPermits;
    private final double identityPermits;
    private final long acquireTimeoutMs;
    private final long logIntervalMs;

    @Autowired
    public RateLimitInterceptor(@Value("${rate.limit.permits:50}") double defaultPermits,
                                @Value("${rate.limit.identity-permits:20}") double identityPermits,
                                @Value("${rate.limit.acquire-timeout-ms:500}") long acquireTimeoutMs,
                                @Value("${rate.limit.log-interval-ms:5000}") long logIntervalMs,
                                RateLimitStore rateLimitStore) {
        this.defaultPermits = defaultPermits > 0 ? defaultPermits : 50.0;
        this.identityPermits = identityPermits > 0 ? identityPermits : 20.0;
        this.acquireTimeoutMs = Math.max(acquireTimeoutMs, 0L);
        this.logIntervalMs = Math.max(logIntervalMs, 0L);
        this.rateLimitStore = rateLimitStore;
    }

    /** 单测/嵌入场景的本地模式兼容构造器。 */
    public RateLimitInterceptor(double defaultPermits, long acquireTimeoutMs, long logIntervalMs) {
        this(defaultPermits, defaultPermits, acquireTimeoutMs, logIntervalMs,
                new RateLimitStore(null, "local"));
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        String path = uri.replaceAll("\\d+", "{id}"); // 参数化路径，共用同一令牌桶
        long halfTimeout = acquireTimeoutMs / 2;

        boolean identityAllowed = rateLimitStore.tryAcquire(
                identityBucketKey(request), identityPermits, halfTimeout);
        boolean pathAllowed = identityAllowed && rateLimitStore.tryAcquire(
                "general:path:" + path, defaultPermits, acquireTimeoutMs - halfTimeout);
        if (identityAllowed && pathAllowed) {
            return true;
        }

        logRejectionThrottled(path);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(
                ApiResponse.error(429, "请求过于频繁，请稍后再试")));
        return false;
    }

    /**
     * 拒绝日志节流：每个 path 在 {@code logIntervalMs} 窗口内最多输出一行，
     * 并附带该窗口内被抑制的拒绝次数（不丢总量信息）。
     *
     * <p>{@code logIntervalMs=0} 表示不节流（每次拒绝都记日志，兼容旧行为 / 排障期可临时开启）。</p>
     */
    private void logRejectionThrottled(String path) {
        Boolean redisShouldLog = rateLimitStore.shouldLogRejectionInRedis(path, logIntervalMs);
        if (redisShouldLog != null) {
            if (redisShouldLog) {
                log.warn("Rate limit exceeded: {} (permits={}/s, identityPermits={}/s, "
                                + "acquireTimeout={}ms, Redis 窗口聚合，多实例每窗口最多一行)",
                        path, defaultPermits, identityPermits, acquireTimeoutMs);
            }
            return;
        }

        PathLogState state = logStates.computeIfAbsent(path, ignored -> new PathLogState());
        state.rejectedSinceLog.incrementAndGet();
        if (logIntervalMs <= 0L) {
            log.warn("Rate limit exceeded: {} (permits={}/s, identityPermits={}/s, "
                            + "acquireTimeout={}ms, 未节流)",
                    path, defaultPermits, identityPermits, acquireTimeoutMs);
            return;
        }
        long now = System.currentTimeMillis();
        long last = state.lastLogAt.get();
        if (now - last >= logIntervalMs && state.lastLogAt.compareAndSet(last, now)) {
            long rejected = state.rejectedSinceLog.getAndSet(0L);
            log.warn("Rate limit exceeded: {} (permits={}/s, identityPermits={}/s, acquireTimeout={}ms, "
                            + "{}ms 窗口内拒绝 {} 次，拒绝日志已节流)",
                    path, defaultPermits, identityPermits, acquireTimeoutMs, logIntervalMs, rejected);
        }
    }

    private static String identityBucketKey(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) {
            return "general:identity:u:" + userId;
        }
        return "general:identity:ip:" + clientIp(request);
    }

    private static String clientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            int comma = value.indexOf(',');
            return (comma > 0 ? value.substring(0, comma) : value).trim();
        }
        return request.getRemoteAddr();
    }

    /** 每个 path 的本地拒绝日志节流状态。 */
    private static final class PathLogState {

        private final AtomicLong rejectedSinceLog = new AtomicLong();
        private final AtomicLong lastLogAt = new AtomicLong(0L);
    }
}
