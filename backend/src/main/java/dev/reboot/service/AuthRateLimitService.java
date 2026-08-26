package dev.reboot.service;

import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 认证入口加固（P1-02-A-1）—— Redis 计数/限流（复用现有 Redis，不新增依赖）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li><b>IP 维度登录/注册限流</b>：Redis 滑动窗口（ZSET，score=毫秒时间戳），
 *       登录 {@code login:attempt:ip:{ip}} 5 分钟最多 30 次；注册
 *       {@code register:attempt:ip:{ip}} 10 分钟最多 10 次；超限抛 429。</li>
 *   <li><b>账号维度登录失败计数与锁定</b>：{@code login:fail:user:{username}}，
 *       失败 +1（TTL 15 分钟），达 5 次后拒绝后续登录（统一 401，不泄露账号状态）。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Service
public class AuthRateLimitService {

    /** 账号失败计数 key 前缀：login:fail:user:{username}。 */
    public static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:user:";
    public static final Duration LOGIN_FAIL_TTL = Duration.ofMinutes(15);
    public static final long MAX_LOGIN_FAILURES = 5;

    /** IP 登录限流 key 前缀：login:attempt:ip:{ip}。 */
    public static final String LOGIN_IP_KEY_PREFIX = "login:attempt:ip:";
    public static final Duration LOGIN_IP_WINDOW = Duration.ofMinutes(5);
    public static final long LOGIN_IP_MAX = 30;

    /** IP 注册限流 key 前缀：register:attempt:ip:{ip}。 */
    public static final String REGISTER_IP_KEY_PREFIX = "register:attempt:ip:";
    public static final Duration REGISTER_IP_WINDOW = Duration.ofMinutes(10);
    public static final long REGISTER_IP_MAX = 10;

    /** 全局每日注册配额 key 前缀：register:daily:{yyyy-MM-dd}（P1-02-A-3）。 */
    public static final String REGISTER_DAILY_KEY_PREFIX = "register:daily:";

    private final StringRedisTemplate redis;
    private final long registerDailyLimit;

    public AuthRateLimitService(StringRedisTemplate redis,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${security.registration.daily-limit:100}") long registerDailyLimit) {
        this.redis = redis;
        this.registerDailyLimit = registerDailyLimit;
    }

    /** IP 维度登录限流；超限抛 429。 */
    public void checkLoginIpLimit(String clientIp) {
        if (isIpLimited(LOGIN_IP_KEY_PREFIX + clientIp, LOGIN_IP_WINDOW, LOGIN_IP_MAX)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }

    /** IP 维度注册限流；超限抛 429。 */
    public void checkRegisterIpLimit(String clientIp) {
        if (isIpLimited(REGISTER_IP_KEY_PREFIX + clientIp, REGISTER_IP_WINDOW, REGISTER_IP_MAX)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }

    /** 全局每日注册配额检查；已达上限抛 429（P1-02-A-3）。 */
    public void checkRegisterDailyQuota() {
        String key = registerDailyKey();
        String v = redis.opsForValue().get(key);
        long count = 0;
        if (v != null) {
            try {
                count = Long.parseLong(v);
            } catch (NumberFormatException ignored) {
                count = 0;
            }
        }
        if (count >= registerDailyLimit) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "注册人数已达今日上限");
        }
    }

    /** 注册成功计数 +1（首次设置 TTL 至次日零点），供每日配额判定。 */
    public void recordRegisterSuccess() {
        String key = registerDailyKey();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, untilNextMidnight());
        }
    }

    private String registerDailyKey() {
        return REGISTER_DAILY_KEY_PREFIX + java.time.LocalDate.now();
    }

    private static Duration untilNextMidnight() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight);
    }

    /** 账号锁定检查：失败计数达 {@link #MAX_LOGIN_FAILURES} → 抛统一 401（不泄露账号状态）。 */
    public void checkUserLoginLocked(String username) {
        String val = redis.opsForValue().get(LOGIN_FAIL_KEY_PREFIX + username);
        if (val != null) {
            try {
                if (Long.parseLong(val) >= MAX_LOGIN_FAILURES) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** 登录失败 +1；首次失败设置 TTL（15 分钟）。 */
    public void recordLoginFailure(String username) {
        String key = LOGIN_FAIL_KEY_PREFIX + username;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, LOGIN_FAIL_TTL);
        }
    }

    /** 登录成功清除失败计数。 */
    public void clearLoginFailure(String username) {
        redis.delete(LOGIN_FAIL_KEY_PREFIX + username);
    }

    /** Redis 滑动窗口（ZSET，score=毫秒时间戳）：裁剪过期事件 → 判定超限 → 未超限则记录本次。 */
    private boolean isIpLimited(String key, Duration window, long max) {
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        redis.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = redis.opsForZSet().zCard(key);
        if (count != null && count >= max) {
            return true;
        }
        redis.opsForZSet().add(key, UUID.randomUUID().toString(), now);
        redis.expire(key, window);
        return false;
    }
}
