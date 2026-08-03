package dev.reboot.config;

import dev.reboot.util.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 密钥配置 —— profile-aware 密钥策略。
 *
 * <h3>密钥策略</h3>
 * <table>
 *   <tr><th>Profile</th><th>来源</th><th>缺密钥行为</th></tr>
 *   <tr><td>test</td><td>application-test.yml → jwt.secret</td><td>使用 YAML 测试密钥</td></tr>
 *   <tr><td>dev</td><td>环境变量 JWT_SECRET</td><td>WARN + fallback 开发密钥</td></tr>
 *   <tr><td>prod</td><td>环境变量 JWT_SECRET（compose .env 注入）</td>
 *       <td><b>抛 IllegalStateException，拒绝启动</b></td></tr>
 * </table>
 *
 * @author hula0710
 * @since 2026-08-04 (Phase 3-A T4)
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Bean
    public JwtUtils jwtUtils() {
        String resolvedSecret = secret;
        if (secret == null || secret.isBlank()) {
            if ("prod".equals(activeProfile)) {
                throw new IllegalStateException(
                        "JWT_SECRET must be set in production. "
                        + "Set it in .env and ensure docker compose injects it.");
            }
            log.warn("JWT_SECRET not set — using development fallback key "
                    + "(profile={}, acceptable for dev only)", activeProfile);
            resolvedSecret = "dev-fallback-key-at-least-256-bits-do-not-use-in-production-environments";
        }
        return new JwtUtils(resolvedSecret, expirationMs);
    }
}
