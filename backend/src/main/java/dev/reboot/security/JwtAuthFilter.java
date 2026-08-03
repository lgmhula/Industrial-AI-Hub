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
 * <p>Token 只解析一次，roles 通过 {@link JwtUtils#getRoles(String)} 提取
 * （内部做类型安全过滤，避免 JJWT 反序列化产生的非 String 元素导致权限校验失败）。
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Component
public class JwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtils jwtUtils;

    public JwtAuthFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtils.validateToken(token)) {
                Claims claims = jwtUtils.parseToken(token);
                request.setAttribute("userId", claims.get("userId", Long.class));
                request.setAttribute("username", claims.getSubject());
                // 使用 jwtUtils.getRoles() 确保类型安全（filter + cast to String）
                request.setAttribute("roles", jwtUtils.getRoles(token));
            } else {
                log.debug("JWT 无效或过期: {}", request.getRequestURI());
            }
        }

        chain.doFilter(request, resp);
    }
}
