package dev.reboot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiRateLimitInterceptor 单测（Day 89 Phase 4 AI 模块重构）。
 * 使用 Spring Test MockHttpServletRequest/MockHttpServletResponse，不启动容器。
 *
 * <p>覆盖 5 场景：普通用户每用户独立桶、匿名按 IP 桶、ADMIN 放宽 5/s、
 * 响应码 429 + JSON body、并发安全（100 用户并行 10 次每桶独立不越流）。</p>
 */
class AiRateLimitInterceptorTest {

    private final String[] AI_PATHS = {
            "/api/ai/chat", "/api/agents/device-status", "/api/rag/ask", "/api/mcp/smoke"
    };

    /** 用极窄限流（0.5 req/s）+ 超时 0 测试"瞬间第二发直接拒绝"，不用 sleep 1000ms。 */
    private static final double NARROW = 0.5;
    private static final double WIDE_ADMIN = 1000; // 对 ADMIN 测试：用大 rate 保证第二发通过
    private static final long TIMEOUT_ZERO = 0L;

    /** 验证"每用户独立桶"：用户 A 被拒绝不影响用户 B（不同 bucket 键）。 */
    @Test
    void separateBuckets_perUser() throws Exception {
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(NARROW, 1e9, TIMEOUT_ZERO);

        // 用户 42 连续 2 发：第一发过，第二发（timeout=0，令牌桶恢复需 2s）直接拒
        boolean r1 = interceptor.preHandle(authReq(42L, List.of("VIEWER"), "/api/ai/chat", "1.1.1.1"),
                res(), new Object());
        boolean r2 = interceptor.preHandle(authReq(42L, List.of("VIEWER"), "/api/ai/chat", "1.1.1.1"),
                res(), new Object());
        assertTrue(r1);
        assertFalse(r2, "同用户 0.5/s 令牌桶第 2 发瞬间拒绝");

        // 用户 43 全新桶：第一发过（和 42 不共享）
        boolean r3 = interceptor.preHandle(authReq(43L, List.of("VIEWER"), "/api/ai/chat", "1.1.1.1"),
                res(), new Object());
        assertTrue(r3, "不同用户使用独立桶，用户 43 不应被用户 42 限流波及");
    }

    /** 匿名（userId=null）：按 IP 建桶。 */
    @Test
    void anonymousBuckets_byIp() throws Exception {
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(NARROW, 1e9, TIMEOUT_ZERO);

        MockHttpServletResponse unused = res();
        // 同一 IP 第 2 发拒绝
        assertTrue(interceptor.preHandle(authReq(null, null, "/api/agents/x", "10.0.0.1"), unused, new Object()));
        assertFalse(interceptor.preHandle(authReq(null, null, "/api/agents/x", "10.0.0.1"), res(), new Object()));

        // 不同 IP：通过
        assertTrue(interceptor.preHandle(authReq(null, null, "/api/agents/x", "10.0.0.2"), unused, new Object()));
    }

    /** ADMIN：放宽到 adminPermits（=1000/s），瞬间两发都过。 */
    @Test
    void adminRole_widerPermits() throws Exception {
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(NARROW, WIDE_ADMIN, TIMEOUT_ZERO);

        boolean r1 = interceptor.preHandle(authReq(1L, List.of("ADMIN"), "/api/ai/chat", "127.0.0.1"),
                res(), new Object());
        boolean r2 = interceptor.preHandle(authReq(1L, List.of("ADMIN"), "/api/ai/chat", "127.0.0.1"),
                res(), new Object());
        assertTrue(r1);
        assertTrue(r2, "ADMIN 桶 1000/s，瞬间第 2 发不应该被限流");
    }

    /** 限流拒绝：429 status + JSON ApiResponse 正确。 */
    @Test
    void limitResponse_status429_and_jsonBody() throws Exception {
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(NARROW, WIDE_ADMIN, TIMEOUT_ZERO);
        HttpServletRequest req = authReq(99L, List.of("VIEWER"), "/api/rag/ask", "127.0.0.1");
        interceptor.preHandle(req, res(), new Object()); // 第 1 发通过

        MockHttpServletResponse res = res();
        boolean proceed = interceptor.preHandle(req, res, new Object()); // 第 2 发拒绝
        assertFalse(proceed);
        assertEquals(429, res.getStatus());
        assertEquals("application/json;charset=UTF-8", res.getContentType());
        String body = res.getContentAsString(StandardCharsets.UTF_8);
        assertNotNull(body);
        assertTrue(body.contains("\"code\":429"), "响应必须包含 429 code 字段: " + body);
        assertTrue(body.contains("AI 接口调用过于频繁"), "响应要说明 AI 限流（不是通用 RateLimit）: " + body);
    }

    /** 并发安全：N 并发用户各 10 次请求 = 每用户令牌桶互相不干扰；每用户大约"每 10 发中 1 发通过 + 9 发拒绝"比例（0.5/s ≈ 0 通过）。
     *  只断言没有抛异常（桶 ConcurrentHashMap putIfAbsent 并发安全）+ 通过总计数 <= N（0.5/s 10 并发 10 次= 最多 N 通过 1 次 × 每人的 0.5 每秒）。 */
    @Test
    void concurrency_multipleUsers_bucketsIndependentSafe() throws Exception {
        final int N_USERS = 20;
        final int REQS_PER_USER = 10;
        final long timeout = 500; // 0.5s，窄限流下基本全拒，无等待不阻塞测试
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(NARROW, 1e9, timeout);

        var pool = Executors.newFixedThreadPool(Math.min(16, N_USERS));
        AtomicInteger passes = new AtomicInteger();
        AtomicInteger rejects = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(N_USERS * REQS_PER_USER);

        for (int u = 0; u < N_USERS; u++) {
            long userId = u + 1;
            for (int r = 0; r < REQS_PER_USER; r++) {
                pool.submit(() -> {
                    try {
                        if (interceptor.preHandle(
                                authReq(userId, List.of("VIEWER"), "/api/ai/chat", "10.0.0." + userId),
                                res(), new Object())) {
                            passes.incrementAndGet();
                        } else {
                            rejects.incrementAndGet();
                        }
                    } catch (Exception any) {
                        fail("并发抛异常: " + any.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS), "并发测试 15s 内未完成");
        pool.shutdownNow();
        assertEquals(N_USERS * REQS_PER_USER, passes.get() + rejects.get(),
                "每个请求要么通过要么拒绝，总和应等于 N*REQ");
        // 0.5/s + 500ms 抢 = 每用户 10 次尝试里可能通过 1 次（first try） + 拒 9 次（其他）
        assertTrue(passes.get() <= N_USERS * 2, "通过次数不应超过用户数 × 2（每人最多开桶 + 一次偶尔抢到）");
    }

    // ============================= 辅助 =============================

    private MockHttpServletRequest authReq(Long userId, List<String> roles, String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        if (userId != null) req.setAttribute("userId", userId);
        if (roles != null) req.setAttribute("roles", roles);
        req.setRemoteAddr(ip);
        return req;
    }

    /**
     * 每次 new 新 response，保证限流写入 body 时不和其他测试共享。
     * PrintWriter 直接用 Mock 的实现（Spring MockHttpServletResponse#getWriter() 已经内建缓存）。
     */
    private static MockHttpServletResponse res() {
        return new MockHttpServletResponse();
    }

    /**
     * 兼容未来：HttpServletResponse.getWriter() 调用返回的 PrintWriter 与 getContentAsString 的桥接已经
     * 由 Spring MockHttpServletResponse 内部完成，不需要额外 Stream 捕获；
     * 保留本类以防未来替换成真实 response，也说明怎么写捕获流。
     */
    @SuppressWarnings("unused")
    private static final class CaptureServletResponseWrapper {
        final HttpServletResponse res;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintWriter writer;

        CaptureServletResponseWrapper(HttpServletResponse res) throws Exception {
            this.res = res;
            this.writer = new PrintWriter(baos, true, StandardCharsets.UTF_8);
        }
    }
}
