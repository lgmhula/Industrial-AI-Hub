package dev.reboot.security;

import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.service.TokenBlacklistService;
import dev.reboot.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAuthFilter 单元测试（P1-02-A-4）。
 *
 * <p>覆盖：正常 token（通过并注入 attrs）、无 jti 旧 token（401）、黑名单命中（401）、
 * 用户撤销（401）、Redis 异常 fail-close（401）、登录/注册公开路径放行。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtUtils jwtUtils;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private Claims claims;

    private JwtAuthFilter filter() {
        return new JwtAuthFilter(jwtUtils, tokenBlacklistService);
    }

    private MockHttpServletRequest request(String uri, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        return req;
    }

    private Claims validClaims() {
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("admin");
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 60_000));
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        return claims;
    }

    @Test
    void validToken_shouldPassAndInjectAttributes() throws Exception {
        validClaims();
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(jwtUtils.parseToken("tok")).thenReturn(claims);
        when(jwtUtils.getRoles("tok")).thenReturn(List.of("ADMIN"));
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(false);
        when(tokenBlacklistService.isUserRevoked(1L, claims.getIssuedAt())).thenReturn(false);

        MockHttpServletRequest req = request("/api/devices", "tok");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus(), "有效 token 不应被拒绝");
        assertNotNull(chain.getRequest(), "应继续进入过滤器链");
        assertEquals(1L, req.getAttribute("userId"));
        assertEquals("jti-1", req.getAttribute("jti"));
        assertNotNull(req.getAttribute("expiration"));
    }

    @Test
    void tokenWithoutJti_shouldReject401() throws Exception {
        when(jwtUtils.validateToken("old-token")).thenReturn(true);
        when(claims.getId()).thenReturn(null); // 无 jti 的存量 token
        when(jwtUtils.parseToken("old-token")).thenReturn(claims);

        MockHttpServletRequest req = request("/api/devices", "old-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(req, resp, new MockFilterChain());

        assertEquals(401, resp.getStatus());
        assertNull(req.getAttribute("userId"));
    }

    @Test
    void blacklistedToken_shouldReject401() throws Exception {
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(jwtUtils.parseToken("tok")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(true);

        MockHttpServletRequest req = request("/api/devices", "tok");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(req, resp, new MockFilterChain());

        assertEquals(401, resp.getStatus());
    }

    @Test
    void revokedUserToken_shouldReject401() throws Exception {
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(jwtUtils.parseToken("tok")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis() - 60_000));
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(false);
        when(tokenBlacklistService.isUserRevoked(1L, claims.getIssuedAt())).thenReturn(true);

        MockHttpServletRequest req = request("/api/devices", "tok");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(req, resp, new MockFilterChain());

        assertEquals(401, resp.getStatus());
    }

    @Test
    void redisError_shouldFailClosed401() throws Exception {
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(jwtUtils.parseToken("tok")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(tokenBlacklistService.isBlacklisted(anyString()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "认证服务异常，请稍后再试"));

        MockHttpServletRequest req = request("/api/devices", "tok");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(req, resp, new MockFilterChain());

        assertEquals(401, resp.getStatus(), "Redis 异常必须 fail-close（401），禁止放行");
    }

    @Test
    void invalidTokenOnLoginPath_shouldContinue() throws Exception {
        when(jwtUtils.validateToken("stale")).thenReturn(false);

        MockHttpServletRequest req = request("/api/auth/login", "stale");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus(), "登录路径携带失效 token 应忽略并继续");
        assertNotNull(chain.getRequest(), "应继续进入过滤器链（不阻断登录）");
    }

    // ===== Day 85 Phase 7: SSE 端点 ?token= query fallback（ADR 0031 §5.2）=====

    /**
     * SSE 端点 + query token 有效 → 注入 attrs（浏览器 EventSource 不支持自定义 header，
     * 必须通过 URL 携带 JWT；Filter 在 /api/push/ 路径下支持 ?token= fallback）。
     */
    @Test
    void sseEndpoint_validQueryToken_shouldPassAndInjectAttributes() throws Exception {
        validClaims();
        when(jwtUtils.validateToken("sse-tok")).thenReturn(true);
        when(jwtUtils.parseToken("sse-tok")).thenReturn(claims);
        when(jwtUtils.getRoles("sse-tok")).thenReturn(List.of("VIEWER"));
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(false);
        when(tokenBlacklistService.isUserRevoked(1L, claims.getIssuedAt())).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/push/inspection");
        req.addParameter("token", "sse-tok"); // query fallback
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus(), "SSE 端点 query token 有效应放行");
        assertNotNull(chain.getRequest(), "应继续进入过滤器链");
        assertEquals(1L, req.getAttribute("userId"), "userId 应注入");
        assertEquals("jti-1", req.getAttribute("jti"));
    }

    /**
     * SSE 端点 + query token 无效 → 401 拒绝（fail-close，与 header token 同语义）。
     */
    @Test
    void sseEndpoint_invalidQueryToken_shouldReject401() throws Exception {
        when(jwtUtils.validateToken("bad")).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/push/inspection");
        req.addParameter("token", "bad");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(req, resp, new MockFilterChain());

        assertEquals(401, resp.getStatus(), "SSE 端点 query token 无效应 401");
    }

    /**
     * SSE 端点 + 无 header 也无 query → 放行（不做鉴权，由 @RequireRole 在 Controller 层拒绝）。
     */
    @Test
    void sseEndpoint_noToken_shouldPassThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/push/inspection");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus(), "无 token 不应被 Filter 拒绝（由 @RequireRole 处理）");
        assertNotNull(chain.getRequest(), "应继续进入过滤器链");
        assertNull(req.getAttribute("userId"), "无 token 不应注入 userId");
    }

    /**
     * 非 SSE 端点（如 /api/devices）即使有 ?token= query 也不应被读取 —— 仅 SSE 端点支持
     * query fallback，避免 token 出现在 REST URL 被日志/Referer 泄漏。
     */
    @Test
    void nonSseEndpoint_queryTokenShouldBeIgnored() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/devices");
        req.addParameter("token", "should-be-ignored");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        assertNotNull(chain.getRequest());
        assertNull(req.getAttribute("userId"), "REST 端点不应读 query token");
        verifyNoInteractions(jwtUtils); // 不应调用任何 JWT 校验
        verifyNoInteractions(tokenBlacklistService);
    }

    /**
     * SSE 端点 + Authorization header + query token 同时存在 → header 优先
     * （避免恶意 query 覆盖合法 header 的攻击路径）。
     */
    @Test
    void sseEndpoint_headerAndQueryPresent_headerWins() throws Exception {
        validClaims();
        when(jwtUtils.validateToken("header-tok")).thenReturn(true);
        when(jwtUtils.parseToken("header-tok")).thenReturn(claims);
        when(jwtUtils.getRoles("header-tok")).thenReturn(List.of("ADMIN"));
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(false);
        when(tokenBlacklistService.isUserRevoked(1L, claims.getIssuedAt())).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/push/inspection");
        req.addHeader("Authorization", "Bearer header-tok");
        req.addParameter("token", "query-tok-should-be-ignored");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(req, new MockHttpServletResponse(), chain);

        // 验证只对 header token 做校验
        verify(jwtUtils).validateToken("header-tok");
        verify(jwtUtils, never()).validateToken("query-tok-should-be-ignored");
        assertEquals(1L, req.getAttribute("userId"));
    }
}
