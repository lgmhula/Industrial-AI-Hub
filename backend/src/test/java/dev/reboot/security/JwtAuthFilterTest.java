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
}
