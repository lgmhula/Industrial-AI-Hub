package dev.reboot.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitInterceptor 单元测试（Day 103）。
 *
 * <p>覆盖 Day 102 压测暴露的「拒绝日志放大」修复，并用手写用例固化 Guava
 * {@code tryAcquire(timeout)} 的真实语义（这一条在 Day 102 曾被误读为「429 会阻塞 500ms」）：</p>
 * <ul>
 *   <li>所需等待 &gt; timeout → **立即**返回 false（不会空等到超时）；</li>
 *   <li>所需等待 ≤ timeout → 睡到令牌可用后返回 true（有界背压队列）。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-10-13
 */
class RateLimitInterceptorTest {

    private static final Object HANDLER = new Object();

    private MockHttpServletResponse invoke(RateLimitInterceptor interceptor, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, HANDLER);
        return response;
    }

    /** 限流额度内放行（preHandle=true，不改写响应状态）。 */
    @Test
    void shouldPassWithinPermits() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(10.0, 500L, 5000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, HANDLER));
        assertEquals(200, response.getStatus(), "未超限不应改写响应状态");
    }

    /** 超限拒绝：429 + ApiResponse JSON 体（保持既有对外契约）。 */
    @Test
    void shouldRejectWith429AndJsonBody() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1.0, 500L, 5000L);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/devices"),
                new MockHttpServletResponse(), HANDLER), "首个请求应拿到初始令牌");

        MockHttpServletResponse response = invoke(interceptor, "/api/devices");

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":429"),
                "响应体应为 ApiResponse JSON: " + response.getContentAsString());
    }

    /**
     * 被拒路径从不阻塞：rate=1/s（下个令牌 1000ms 后）而 timeout=500ms
     * → Guava 立即返回 false，10 次拒绝应在毫秒级完成（而非 10×500ms）。
     */
    @Test
    void shouldRejectImmediatelyWhenRequiredWaitExceedsTimeout() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1.0, 500L, 60_000L);
        invoke(interceptor, "/api/devices"); // 消耗唯一令牌

        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            assertEquals(429, invoke(interceptor, "/api/devices").getStatus());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 300, "10 次拒绝耗时 " + elapsedMs + "ms，被拒路径不应等待 timeout");
    }

    /**
     * 有界背压队列：rate=10/s（下个令牌 100ms 后）而 timeout=500ms
     * → 等待约 100ms 后**放行**（这正是 Day 102 里 ~500ms 延迟只出现在 200 上的原因）。
     */
    @Test
    void shouldWaitAndAdmitWhenPermitArrivesWithinTimeout() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(10.0, 500L, 60_000L);
        invoke(interceptor, "/api/devices"); // 消耗初始令牌，下个令牌约 100ms 后

        long start = System.nanoTime();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        boolean admitted = interceptor.preHandle(request, new MockHttpServletResponse(), HANDLER);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(admitted, "等待 ≤ timeout 时应等到令牌并放行");
        assertTrue(elapsedMs >= 50, "应等待约 100ms 才拿到令牌，实际 " + elapsedMs + "ms");
    }

    /** P0 修复：窗口内大量拒绝只输出一行日志。 */
    @Test
    void shouldThrottleRejectionLogWithinWindow() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1.0, 500L, 60_000L);
        invoke(interceptor, "/api/devices"); // 消耗唯一令牌

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            for (int i = 0; i < 50; i++) {
                assertEquals(429, invoke(interceptor, "/api/devices").getStatus());
            }
        } finally {
            detachAppender(appender);
        }

        assertEquals(1, appender.list.size(), "60s 窗口内 50 次拒绝应只记 1 行");
        assertTrue(appender.list.get(0).getFormattedMessage().contains("节流"),
                "日志应标明已节流: " + appender.list.get(0).getFormattedMessage());
    }

    /** 抑制条数不丢：下一个窗口首次拒绝时汇总上一个窗口被抑制的次数。 */
    @Test
    void shouldReportSuppressedCountInNextWindow() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1.0, 500L, 100L);
        invoke(interceptor, "/api/devices"); // 消耗唯一令牌

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            for (int round = 0; round < 3; round++) {
                for (int i = 0; i < 5; i++) {
                    assertEquals(429, invoke(interceptor, "/api/devices").getStatus());
                }
                Thread.sleep(160L); // 跨过 100ms 日志窗口
            }
        } finally {
            detachAppender(appender);
        }

        assertTrue(appender.list.size() >= 2, "跨窗口后应产生多行日志，实际 " + appender.list.size());
        long maxReported = 0L;
        Pattern p = Pattern.compile("拒绝 (\\d+) 次");
        for (ILoggingEvent event : appender.list) {
            Matcher m = p.matcher(event.getFormattedMessage());
            if (m.find()) {
                maxReported = Math.max(maxReported, Long.parseLong(m.group(1)));
            }
        }
        assertTrue(maxReported > 1, "后续窗口日志应汇总被抑制的条数（实际最大汇总=" + maxReported + "）");
    }

    /** log-interval-ms=0 → 不节流（每次拒绝都记日志，兼容旧行为 / 排障期）。 */
    @Test
    void shouldLogEveryRejectionWhenThrottleDisabled() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1.0, 500L, 0L);
        invoke(interceptor, "/api/devices"); // 消耗唯一令牌

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            for (int i = 0; i < 5; i++) {
                assertEquals(429, invoke(interceptor, "/api/devices").getStatus());
            }
        } finally {
            detachAppender(appender);
        }

        assertEquals(5, appender.list.size(), "未节流时每次拒绝都应记日志");
    }

    /** 参数化路径共用同一令牌桶：/api/devices/1 与 /api/devices/2 共享额度。 */
    @Test
    void shouldShareBucketAcrossNumericPathSegments() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1.0, 500L, 5000L);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/devices/1"),
                new MockHttpServletResponse(), HANDLER));
        assertEquals(429, invoke(interceptor, "/api/devices/2").getStatus(),
                "数字段应被归一化为 {id}，共用桶");
    }

    /** 身份桶：同一用户不能吃满路径额度；不同用户使用独立身份桶。 */
    @Test
    void shouldLimitEachIdentityIndependently() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                100.0, 1.0, 200L, 5000L, new RateLimitStore(null, "local"));

        MockHttpServletRequest user1 = new MockHttpServletRequest("GET", "/api/devices");
        user1.setAttribute("userId", 1L);
        assertTrue(interceptor.preHandle(user1, new MockHttpServletResponse(), HANDLER));
        assertEquals(429, invokeAsUser(interceptor, "/api/devices", 1L).getStatus());

        assertTrue(interceptor.preHandle(userReq("/api/devices", 2L),
                new MockHttpServletResponse(), HANDLER), "不同用户应使用独立身份桶");
    }

    /** 非法配置回退默认值，不抛异常。 */
    @Test
    void shouldFallbackOnIllegalPermits() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(0.0, 500L, 5000L);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/devices"),
                new MockHttpServletResponse(), HANDLER), "permits<=0 应回退默认 50 而非抛异常");
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(RateLimitInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(RateLimitInterceptor.class);
        logger.detachAppender(appender);
    }

    private MockHttpServletResponse invokeAsUser(
            RateLimitInterceptor interceptor, String uri, long userId) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(userReq(uri, userId), response, HANDLER);
        return response;
    }

    private MockHttpServletRequest userReq(String uri, long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setAttribute("userId", userId);
        return request;
    }
}
