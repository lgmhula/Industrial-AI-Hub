package dev.reboot.service;

import dev.reboot.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthRateLimitService 单元测试（P1-02-A-1）。
 *
 * <p>覆盖：IP 滑动窗口登录/注册限流（429）、账号失败计数（5 次锁定 / TTL / 清除）。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class AuthRateLimitServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private ValueOperations<String, String> valueOps;

    private AuthRateLimitService service() {
        return new AuthRateLimitService(redis, 100);
    }

    /* ---- IP 登录限流（滑动窗口）---- */

    @Test
    void checkLoginIpLimit_underMax_shouldPassAndRecordEvent() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard("login:attempt:ip:1.2.3.4")).thenReturn(5L);

        assertDoesNotThrow(() -> service().checkLoginIpLimit("1.2.3.4"));
        verify(zSetOps).removeRangeByScore(eq("login:attempt:ip:1.2.3.4"), eq(0.0), anyDouble());
        verify(zSetOps).add(eq("login:attempt:ip:1.2.3.4"), anyString(), anyDouble());
        verify(redis).expire("login:attempt:ip:1.2.3.4", AuthRateLimitService.LOGIN_IP_WINDOW);
    }

    @Test
    void checkLoginIpLimit_atMax_shouldThrow429() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard("login:attempt:ip:1.2.3.4")).thenReturn(AuthRateLimitService.LOGIN_IP_MAX);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().checkLoginIpLimit("1.2.3.4"));
        assertEquals(429, ex.getErrorCode().getCode());
        verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void checkRegisterIpLimit_atMax_shouldThrow429() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard("register:attempt:ip:9.9.9.9")).thenReturn(AuthRateLimitService.REGISTER_IP_MAX);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().checkRegisterIpLimit("9.9.9.9"));
        assertEquals(429, ex.getErrorCode().getCode());
    }

    /* ---- 账号失败计数与锁定 ---- */

    @Test
    void recordLoginFailure_firstFailure_shouldSetTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:fail:user:alice")).thenReturn(1L);

        service().recordLoginFailure("alice");

        verify(redis).expire("login:fail:user:alice", AuthRateLimitService.LOGIN_FAIL_TTL);
    }

    @Test
    void recordLoginFailure_subsequentFailures_shouldNotRefreshTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:fail:user:alice")).thenReturn(4L);

        service().recordLoginFailure("alice");

        verify(redis, never()).expire(anyString(), any());
    }

    @Test
    void fiveFailures_shouldLockAccount() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:fail:user:alice")).thenReturn(1L, 2L, 3L, 4L, 5L);
        when(valueOps.get("login:fail:user:alice")).thenReturn("5");

        AuthRateLimitService svc = service();
        for (int i = 0; i < AuthRateLimitService.MAX_LOGIN_FAILURES; i++) {
            svc.recordLoginFailure("alice");
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.checkUserLoginLocked("alice"));
        assertEquals(401, ex.getErrorCode().getCode());
    }

    @Test
    void checkUserLoginLocked_shouldPassWhenNotLocked() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("login:fail:user:bob")).thenReturn(null);
        assertDoesNotThrow(() -> service().checkUserLoginLocked("bob"));
    }

    @Test
    void clearLoginFailure_shouldDeleteKey() {
        when(redis.delete("login:fail:user:alice")).thenReturn(true);
        service().clearLoginFailure("alice");
        verify(redis).delete("login:fail:user:alice");
    }

    /* ---- 全局每日注册配额（P1-02-A-3） ---- */

    @Test
    void checkRegisterDailyQuota_underLimit_shouldPass() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(AuthRateLimitService.REGISTER_DAILY_KEY_PREFIX + java.time.LocalDate.now()))
                .thenReturn("50");
        assertDoesNotThrow(() -> service().checkRegisterDailyQuota());
    }

    @Test
    void checkRegisterDailyQuota_atLimit_shouldThrow429() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(AuthRateLimitService.REGISTER_DAILY_KEY_PREFIX + java.time.LocalDate.now()))
                .thenReturn("100");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().checkRegisterDailyQuota());
        assertEquals(429, ex.getErrorCode().getCode());
    }

    @Test
    void recordRegisterSuccess_shouldIncrementAndExpireOnFirst() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(AuthRateLimitService.REGISTER_DAILY_KEY_PREFIX + java.time.LocalDate.now()))
                .thenReturn(1L);
        service().recordRegisterSuccess();
        verify(redis).expire(eq(AuthRateLimitService.REGISTER_DAILY_KEY_PREFIX + java.time.LocalDate.now()), any());
    }
}
