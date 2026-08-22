package dev.reboot.service;

import dev.reboot.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TokenBlacklistService 单元测试（P1-02-A-4）。
 *
 * <p>覆盖：单 token 黑名单（TTL）、用户级撤销（epoch 秒 + TTL 24h）、iat 比较、
 * Redis 异常 fail-close（401）。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenBlacklistService service() {
        return new TokenBlacklistService(redis);
    }

    @Test
    void blacklistToken_shouldSetWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service().blacklistToken("jti-1", Duration.ofMinutes(30));
        verify(valueOps).set("token:blacklist:jti-1", "1", Duration.ofMinutes(30));
    }

    @Test
    void blacklistToken_negativeTtl_shouldUseOneMinute() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service().blacklistToken("jti-expired", Duration.ofMinutes(-5));
        verify(valueOps).set("token:blacklist:jti-expired", "1", Duration.ofMinutes(1));
    }

    @Test
    void isBlacklisted_shouldReturnTrueWhenKeyExists() {
        when(redis.hasKey("token:blacklist:jti-1")).thenReturn(true);
        assertTrue(service().isBlacklisted("jti-1"));
    }

    @Test
    void revokeUser_shouldSetEpochWith24hTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service().revokeUser(7L);
        verify(valueOps).set(eq("revoke:user:7"), anyString(), eq(TokenBlacklistService.USER_REVOKE_TTL));
    }

    @Test
    void isUserRevoked_issuedBeforeRevoke_shouldBeTrue() {
        when(redis.opsForValue()).thenReturn(valueOps);
        long revokeAt = Instant.now().getEpochSecond();
        when(valueOps.get("revoke:user:7")).thenReturn(String.valueOf(revokeAt));
        Date issuedBefore = new Date((revokeAt - 60) * 1000);
        assertTrue(service().isUserRevoked(7L, issuedBefore));
    }

    @Test
    void isUserRevoked_issuedAfterRevoke_shouldBeFalse() {
        when(redis.opsForValue()).thenReturn(valueOps);
        long revokeAt = Instant.now().getEpochSecond();
        when(valueOps.get("revoke:user:7")).thenReturn(String.valueOf(revokeAt));
        Date issuedAfter = new Date((revokeAt + 60) * 1000);
        assertFalse(service().isUserRevoked(7L, issuedAfter));
    }

    @Test
    void isUserRevoked_noMarker_shouldBeFalse() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("revoke:user:7")).thenReturn(null);
        assertFalse(service().isUserRevoked(7L, new Date()));
    }

    @Test
    void isUserRevoked_corruptMarker_shouldFailClosed() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("revoke:user:7")).thenReturn("not-a-number");
        assertTrue(service().isUserRevoked(7L, new Date()), "数据异常应保守视为已撤销（fail-close）");
    }

    @Test
    void isBlacklisted_redisError_shouldFailClosed() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service().isBlacklisted("jti-1"));
        assertEquals(401, ex.getErrorCode().getCode(), "Redis 异常必须 fail-close（认证失败），禁止放行");
    }
}
