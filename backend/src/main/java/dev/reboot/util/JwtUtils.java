package dev.reboot.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类 —— 生成、验证、解析 Token（含角色信息）。
 *
 * <h3>安全说明</h3>
 * <p>签名密钥通过环境变量 {@code JWT_SECRET} 注入，不硬编码在源码中。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
public final class JwtUtils {

    private static final SecretKey KEY;
    private static final long EXPIRATION_MS;

    static {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            String msg = "[WARN] JWT_SECRET 未设置，使用开发默认密钥——生产环境必须配置!";
            System.err.println(msg);
            secret = "DevOnly-DefaultKey-DoNotUseInProduction-ChangeMe-256bit!";
        }
        KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String expirationEnv = System.getenv("JWT_EXPIRATION_MS");
        EXPIRATION_MS = (expirationEnv != null && !expirationEnv.isBlank())
                ? Long.parseLong(expirationEnv)
                : 24 * 60 * 60 * 1000L;
    }

    private JwtUtils() {}

    /**
     * 生成带角色信息的 JWT Token。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roles    角色代码列表（如 ["ADMIN"]）
     * @return JWT 字符串
     */
    public static String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles != null ? roles : Collections.emptyList())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token 中的 Claims。
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效。
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 从 Token 中提取用户名。
     */
    public static String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从 Token 中提取用户 ID。
     */
    public static Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /**
     * 从 Token 中提取角色代码列表。
     */
    public static List<String> getRoles(String token) {
        Claims claims = parseToken(token);
        List<?> raw = claims.get("roles", List.class);
        if (raw == null) {
            return Collections.emptyList();
        }
        return raw.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
