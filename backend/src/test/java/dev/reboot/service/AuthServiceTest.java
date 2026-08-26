package dev.reboot.service;

import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.entity.User;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 登录路径单元测试（P1-02-A-1 入口加固 + P1-02-A-2 持久锁定）。
 *
 * <p>注：注册治理（开关/邀请码/配额）相关用例见 {@link AuthRegistrationGovernanceTest}
 * （本类 @InjectMocks 无法注入 registrationEnabled/inviteCode，故注册用例单独构造 service）。</p>
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuthRateLimitService authRateLimitService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private LoginAuditService loginAuditService;
    private AuthService authService;

    private static final String USER_AGENT = "test-agent";

    /** 手工构造（@Value 参数无法经 @InjectMocks 注入；登录路径与注册开关无关，取默认 false/null）。 */
    private AuthService service() {
        authService = new AuthService(userMapper, userRoleMapper, passwordEncoder, jwtUtils,
                authRateLimitService, tokenBlacklistService, loginAuditService, false, null);
        return authService;
    }

    private static final String CLIENT_IP = "1.2.3.4";

    private User activeUser(Long id, Integer failedAttempts) {
        User u = new User();
        u.setId(id);
        u.setUsername("admin");
        u.setPassword("encoded");
        u.setStatus(1);
        u.setFailedAttempts(failedAttempts);
        u.setLockedUntil(null);
        return u;
    }

    /* ============ 登录成功 ============ */

    @Test
    void login_shouldReturnTokenOnSuccess() {
        User u = activeUser(1L, 0);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(jwtUtils.generateToken(eq(1L), eq("admin"), eq(List.of("ADMIN")))).thenReturn("eyJ.mocked.token");

        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("pass");
        String token = service().login(req, CLIENT_IP, USER_AGENT);

        assertNotNull(token);
        assertTrue(token.startsWith("eyJ"));
        verify(authRateLimitService).checkLoginIpLimit(CLIENT_IP);
        verify(authRateLimitService).clearLoginFailure("admin");
        verify(userMapper).resetLoginSecurity(1L);
    }

    /* ============ 统一 401（账户枚举防护） ============ */

    @Test
    void login_shouldThrowWhenUserNotFound() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);
        LoginRequest req = new LoginRequest(); req.setUsername("ghost"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        assertEquals(401, ex.getErrorCode().getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(authRateLimitService).recordLoginFailure("ghost");
        // 不存在用户无 DB 行可更新
        verify(userMapper, never()).updateFailedAttempts(anyLong(), anyInt());
    }

    @Test
    void login_disabledUser_shouldReturnUnified401_noAccountLeak() {
        User u = activeUser(2L, null);
        u.setUsername("banned");
        u.setStatus(0);
        when(userMapper.findByUsername("banned")).thenReturn(u);
        LoginRequest req = new LoginRequest(); req.setUsername("banned"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        assertEquals(401, ex.getErrorCode().getCode());
        assertEquals("用户名或密码错误", ex.getMessage(), "禁用状态不得泄露（不得出现「账户已禁用」文案）");
        verify(authRateLimitService).recordLoginFailure("banned");
        verify(userMapper).updateFailedAttempts(2L, 1);
    }

    @Test
    void login_shouldThrowWhenWrongPassword() {
        User u = activeUser(3L, 0);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("wrong");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        assertEquals(401, ex.getErrorCode().getCode());
        verify(authRateLimitService).recordLoginFailure("admin");
        verify(userMapper).updateFailedAttempts(3L, 1);
    }

    @Test
    void login_whenRedisLocked_shouldReturnUnified401() {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"))
                .when(authRateLimitService).checkUserLoginLocked("admin");
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        assertEquals(401, ex.getErrorCode().getCode());
        verify(userMapper, never()).findByUsername(anyString());
    }

    @Test
    void login_whenDbLocked_shouldReturnUnified401() {
        User u = activeUser(4L, 5);
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userMapper.findByUsername("admin")).thenReturn(u);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        assertEquals(401, ex.getErrorCode().getCode());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userMapper, never()).updateFailedAttempts(anyLong(), anyInt());
    }

    /* ============ 持久失败计数与锁定（P1-02-A-2） ============ */

    @Test
    void login_failure_shouldIncrementDbFailedAttempts() {
        User u = activeUser(5L, 2);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("wrong");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(userMapper).updateFailedAttempts(5L, 3);
        verify(userMapper, never()).updateLockedUntil(anyLong(), any());
    }

    @Test
    void login_fiveFailures_shouldSetPersistentLock() {
        User u = activeUser(6L, (int) AuthRateLimitService.MAX_LOGIN_FAILURES - 1);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("wrong");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(userMapper).updateFailedAttempts(6L, (int) AuthRateLimitService.MAX_LOGIN_FAILURES);
        verify(userMapper).updateLockedUntil(eq(6L), notNull());
    }

    /* ============ IP 限流 / 注册 ============ */

    @Test
    void login_ipLimited_shouldThrow429() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"))
                .when(authRateLimitService).checkLoginIpLimit(CLIENT_IP);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        assertEquals(429, ex.getErrorCode().getCode());
    }

    @Test
    void register_ipLimited_shouldThrow429() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"))
                .when(authRateLimitService).checkRegisterIpLimit(CLIENT_IP);
        RegisterRequest req = new RegisterRequest(); req.setUsername("newuser"); req.setPassword("123456");
        BusinessException ex = assertThrows(BusinessException.class, () -> service().register(req, CLIENT_IP));
        assertEquals(429, ex.getErrorCode().getCode());
    }

    @Test
    void logout_shouldBlacklistToken() {
        service().logout("jti-abc", java.time.Duration.ofMinutes(30));
        verify(tokenBlacklistService).blacklistToken("jti-abc", java.time.Duration.ofMinutes(30));
    }

    @Test
    void logout_blankJti_shouldBeNoop() {
        service().logout("  ", java.time.Duration.ofMinutes(30));
        verify(tokenBlacklistService, never()).blacklistToken(anyString(), any());
    }

    /* ============ P1-02-A-5 登录审计 reason ============ */

    @Test
    void login_success_shouldAuditSuccess() {
        User u = activeUser(1L, 0);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(jwtUtils.generateToken(eq(1L), eq("admin"), eq(List.of("ADMIN")))).thenReturn("tok");

        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("pass");
        service().login(req, CLIENT_IP, USER_AGENT);

        verify(loginAuditService).record(1L, "admin", true, CLIENT_IP, USER_AGENT,
                LoginAuditService.REASON_SUCCESS);
    }

    @Test
    void login_notFound_shouldAuditInvalidCredential() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);
        LoginRequest req = new LoginRequest(); req.setUsername("ghost"); req.setPassword("x");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(loginAuditService).record(isNull(), eq("ghost"), eq(false), eq(CLIENT_IP), eq(USER_AGENT),
                eq(LoginAuditService.REASON_INVALID_CREDENTIAL));
    }

    @Test
    void login_wrongPassword_shouldAuditInvalidPassword() {
        User u = activeUser(3L, 0);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("wrong");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(loginAuditService).record(3L, "admin", false, CLIENT_IP, USER_AGENT,
                LoginAuditService.REASON_INVALID_PASSWORD);
    }

    @Test
    void login_disabled_shouldAuditAccountDisabled() {
        User u = activeUser(2L, null);
        u.setUsername("banned");
        u.setStatus(0);
        when(userMapper.findByUsername("banned")).thenReturn(u);
        LoginRequest req = new LoginRequest(); req.setUsername("banned"); req.setPassword("x");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(loginAuditService).record(2L, "banned", false, CLIENT_IP, USER_AGENT,
                LoginAuditService.REASON_ACCOUNT_DISABLED);
    }

    @Test
    void login_dbLocked_shouldAuditAccountLocked() {
        User u = activeUser(4L, 5);
        u.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(userMapper.findByUsername("admin")).thenReturn(u);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(loginAuditService).record(4L, "admin", false, CLIENT_IP, USER_AGENT,
                LoginAuditService.REASON_ACCOUNT_LOCKED);
    }

    @Test
    void login_ipLimited_shouldAuditRateLimit() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"))
                .when(authRateLimitService).checkLoginIpLimit(CLIENT_IP);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        assertThrows(BusinessException.class, () -> service().login(req, CLIENT_IP, USER_AGENT));
        verify(loginAuditService).record(isNull(), eq("admin"), eq(false), eq(CLIENT_IP), eq(USER_AGENT),
                eq(LoginAuditService.REASON_RATE_LIMIT));
    }
}
