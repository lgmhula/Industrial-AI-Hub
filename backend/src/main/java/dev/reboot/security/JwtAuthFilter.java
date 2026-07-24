package dev.reboot.security;

import dev.reboot.util.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JWT 认证过滤器 —— 从 Authorization 头提取 Token，解析后注入 request attribute。
 *
 * <p>不拦截未登录请求，由 AuthInterceptor 做权限判断。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
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
                request.setAttribute("userId", JwtUtils.getUserId(token));
                request.setAttribute("username", JwtUtils.getUsername(token));
                request.setAttribute("roles", JwtUtils.getRoles(token));
            } else {
                log.debug("JWT 无效或过期: {}", request.getRequestURI());
            }
        }

        chain.doFilter(request, resp);
    }
}
