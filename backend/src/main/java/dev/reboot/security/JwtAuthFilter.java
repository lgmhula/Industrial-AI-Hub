package dev.reboot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.dto.ApiResponse;
import dev.reboot.service.TokenBlacklistService;
import dev.reboot.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证过滤器 —— 验签/过期 + 生命周期校验（P1-02-A-4）。
 *
 * <p>校验流程：验签/过期 → 读取 jti（无 jti 的旧 token → 401）→ 黑名单 → 用户撤销
 * （token.iat &lt; revoke 标记）→ 通过后注入 userId/username/roles/jti/expiration 到 request attributes。</p>
 *
 * <p>行为约定：</p>
 * <ul>
 *   <li>无 Authorization 头 → 放行（由 {@link AuthInterceptor}/@RequireRole 处理）；</li>
 *   <li>带 token 但无效/被撤销/Redis 异常（fail-close）→ 直接 401（登录/注册公开路径除外，避免陈旧 token 阻断登录）；</li>
 *   <li>登录/注册（/api/auth/login|register）携带失效 token → 忽略 token 继续（保持可登录）。</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Component
public class JwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String REJECT_MESSAGE = "登录状态已失效，请重新登录";

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthFilter(JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            if (!jwtUtils.validateToken(token)) {
                rejectIfNotPublicAuth(request, response, chain);
                return;
            }
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                // 无 jti 的存量 token（P1-02-A-4 升级前签发）→ 拒绝，强制重新登录
                rejectIfNotPublicAuth(request, response, chain);
                return;
            }
            if (tokenBlacklistService.isBlacklisted(jti)) {
                rejectIfNotPublicAuth(request, response, chain);
                return;
            }
            Long userId = claims.get("userId", Long.class);
            if (userId != null && tokenBlacklistService.isUserRevoked(userId, claims.getIssuedAt())) {
                rejectIfNotPublicAuth(request, response, chain);
                return;
            }

            request.setAttribute("userId", userId);
            request.setAttribute("username", claims.getSubject());
            // 使用 jwtUtils.getRoles() 确保类型安全
            request.setAttribute("roles", jwtUtils.getRoles(token));
            request.setAttribute("jti", jti);
            request.setAttribute("expiration", claims.getExpiration());
        } catch (dev.reboot.exception.BusinessException e) {
            // fail-close：Redis 异常等 → 认证失败，禁止放行
            log.warn("JWT 校验失败（fail-close）: {} {} {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            rejectIfNotPublicAuth(request, response, chain);
            return;
        } catch (Exception e) {
            log.warn("JWT 解析异常: {} {} {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            rejectIfNotPublicAuth(request, response, chain);
            return;
        }

        chain.doFilter(request, response);
    }

    /** 登录/注册公开路径：失效 token 忽略放行（避免陈旧 token 阻断登录）；其余路径 401 拒绝。 */
    private void rejectIfNotPublicAuth(HttpServletRequest request, HttpServletResponse response,
                                       FilterChain chain) throws IOException, ServletException {
        String uri = request.getRequestURI();
        if (uri.endsWith("/api/auth/login") || uri.endsWith("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.error(401, REJECT_MESSAGE)));
    }
}
