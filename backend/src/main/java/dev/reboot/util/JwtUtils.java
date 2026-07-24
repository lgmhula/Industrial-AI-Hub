package dev.reboot.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 生成、验证、解析 Token。
 *
 * <h3>安全说明</h3>
 * <p>签名密钥通过环境变量 {@code JWT_SECRET} 注入，不硬编码在源码中。
 * 生产环境必须设置至少 256-bit 长度的密钥。</p>
 *
 * <h3>依赖</h3>
 * <ul>
 *   <li>jjwt-api 0.12.6</li>
 *   <li>jjwt-impl + jjwt-jackson (runtime)</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-21
 */
public final class JwtUtils {

    private static final SecretKey KEY;

    /** Token 有效期，通过环境变量 JWT_EXPIRATION_MS 覆盖，默认 24 小时 */
    private static final long EXPIRATION_MS;

    static {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            System.err.println("[WARN] JWT_SECRET 未设置，使用开发默认密钥（不安全）");
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
     * 生成 JWT Token。
     *
     * @param userId   用户 ID
     * @param username 用户名（存入 subject）
     * @return JWT 字符串
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token 中的 Claims。
     *
     * @param token JWT 字符串
     * @return Claims 对象
     * @throws JwtException 若 Token 无效或过期
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效（签名正确且未过期）。
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
}
