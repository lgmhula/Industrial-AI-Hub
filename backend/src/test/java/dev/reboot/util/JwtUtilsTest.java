package dev.reboot.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtils 单元测试（P1-02-A-4：jti 生成）。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
class JwtUtilsTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";
    private static final long EXPIRATION_MS = 3600_000L;

    private JwtUtils newUtils() {
        return new JwtUtils(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_shouldContainJti() {
        JwtUtils utils = newUtils();
        String token = utils.generateToken(1L, "admin", List.of("ADMIN"));
        Claims claims = utils.parseToken(token);

        assertNotNull(claims.getId(), "token 必须包含 jti（P1-02-A-4 黑名单/撤销基准）");
        assertEquals("admin", claims.getSubject());
        assertEquals(1L, claims.get("userId", Long.class));
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis(), "exp 应在未来");
    }

    @Test
    void generateToken_shouldHaveUniqueJtiPerToken() {
        JwtUtils utils = newUtils();
        String jti1 = utils.parseToken(utils.generateToken(1L, "a", List.of("ADMIN"))).getId();
        String jti2 = utils.parseToken(utils.generateToken(1L, "a", List.of("ADMIN"))).getId();
        assertNotEquals(jti1, jti2, "每次签发应生成唯一 jti");
    }
}
