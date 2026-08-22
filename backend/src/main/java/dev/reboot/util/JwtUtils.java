package dev.reboot.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类 —— 生成、验证、解析 Token（含角色信息）。
 *
 * <h3>安全说明</h3>
 * <p>签名密钥由 {@link dev.reboot.config.JwtConfig} 通过构造器注入，
 * 遵循 profile-aware 策略：test 用 YAML 固定密钥，dev 允许 fallback，prod 拒绝启动。</p>
 *
 * <h3>Phase 3-A T4 变更</h3>
 * <p>从 static 工具类重构为 Spring Bean（构造器注入密钥），
 * 不再使用 {@code System.getenv()} 读取环境变量。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    private final SecretKey key;
    private final long expirationMs;

    /**
     * 构造器注入 —— 由 {@link dev.reboot.config.JwtConfig#jwtUtils()} 调用。
     *
     * @param secret       JWT 签名密钥（HS256 需 ≥256 bits）
     * @param expirationMs Token 过期时间（毫秒），默认 24h
     */
    public JwtUtils(String secret, long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 生成带角色信息的 JWT Token（P1-02-A-4：含 jti 用于黑名单/撤销）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roles    角色代码列表（如 ["ADMIN"]）
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString()) // jti（登出黑名单/用户撤销基准）
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles != null ? roles : Collections.emptyList())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token 中的 Claims。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效。
     */
    public boolean validateToken(String token) {
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
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从 Token 中提取用户 ID。
     */
    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /**
     * 从 Token 中提取角色代码列表（类型安全过滤）。
     */
    public List<String> getRoles(String token) {
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
