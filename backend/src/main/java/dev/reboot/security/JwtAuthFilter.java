package dev.reboot.security;

import dev.reboot.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证过滤器 —— 从 Authorization 头提取 Token，解析后注入 request attribute。
 *
 * <p>Token 只解析一次，Claims 结果复用（避免 4 次重复解析）。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Component
public class JwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (JwtUtils.validateToken(token)) {
                Claims claims = JwtUtils.parseToken(token);
                request.setAttribute("userId", claims.get("userId", Long.class));
                request.setAttribute("username", claims.getSubject());
                @SuppressWarnings("unchecked")
                java.util.List<String> roles = claims.get("roles", java.util.List.class);
                request.setAttribute("roles", roles);
            } else {
                log.debug("JWT 无效或过期: {}", request.getRequestURI());
            }
        }

        chain.doFilter(request, resp);
    }
}
