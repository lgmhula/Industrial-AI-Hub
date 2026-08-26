package dev.reboot.service;

import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生命周期治理（P1-02-A-4）—— Redis 黑名单与用户级撤销。
 *
 * <p>Key 设计：</p>
 * <ul>
 *   <li>{@code token:blacklist:{jti}}：登出单 token 黑名单，TTL = 剩余有效期</li>
 *   <li>{@code revoke:user:{userId}}：用户级撤销标记（epoch 秒），TTL 24h；
 *       token.iat &lt; 标记 → 撤销（禁用/改密）</li>
 * </ul>
 *
 * <p><b>fail-close</b>：Redis 异常一律抛 {@link BusinessException}(401)，
 * 禁止静默放行（避免撤销失效导致旧 token 绕过）。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Service
public class TokenBlacklistService {

    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    public static final String USER_REVOKE_PREFIX = "revoke:user:";
    public static final Duration USER_REVOKE_TTL = Duration.ofHours(24);

    private static final String REDIS_ERROR_MESSAGE = "认证服务异常，请稍后再试";

    private final StringRedisTemplate redis;

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 单 token 黑名单（登出）。 */
    public void blacklistToken(String jti, Duration ttl) {
        try {
            redis.opsForValue().set(TOKEN_BLACKLIST_PREFIX + jti, "1",
                    ttl.isNegative() ? Duration.ofMinutes(1) : ttl);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, REDIS_ERROR_MESSAGE);
        }
    }

    /** 单 token 是否黑名单（登出后）。 */
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(TOKEN_BLACKLIST_PREFIX + jti));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, REDIS_ERROR_MESSAGE);
        }
    }

    /** 用户级撤销（禁用/改密）：写入撤销时刻（epoch 秒），TTL 24h。 */
    public void revokeUser(Long userId) {
        try {
            redis.opsForValue().set(USER_REVOKE_PREFIX + userId,
                    String.valueOf(Instant.now().getEpochSecond()), USER_REVOKE_TTL);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, REDIS_ERROR_MESSAGE);
        }
    }

    /**
     * 用户级撤销检查：存在撤销标记且 token 签发时间早于撤销时刻 → 已撤销。
     */
    public boolean isUserRevoked(Long userId, Date issuedAt) {
        try {
            String value = redis.opsForValue().get(USER_REVOKE_PREFIX + userId);
            if (value == null) {
                return false;
            }
            long revokeAt;
            try {
                revokeAt = Long.parseLong(value);
            } catch (NumberFormatException e) {
                return true; // 数据异常 → 保守视为已撤销（fail-close）
            }
            return issuedAt != null && issuedAt.getTime() / 1000 < revokeAt;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, REDIS_ERROR_MESSAGE);
        }
    }
}
