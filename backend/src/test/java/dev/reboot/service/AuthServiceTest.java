package dev.reboot.service;

import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试（P1-02-A-1 入口加固）。
 *
 * <p>覆盖：统一 401（不存在/禁用/密码错/锁定）、失败计数记录/清除、IP 限流 429、注册限流。</p>
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
    @InjectMocks private AuthService authService;

    private static final String CLIENT_IP = "1.2.3.4";

    @Test
    void login_shouldReturnTokenOnSuccess() {
        User u = new User(); u.setId(1L); u.setUsername("admin"); u.setPassword("encoded"); u.setStatus(1);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(jwtUtils.generateToken(eq(1L), eq("admin"), eq(List.of("ADMIN")))).thenReturn("eyJ.mocked.token");

        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("pass");
        String token = authService.login(req, CLIENT_IP);

        assertNotNull(token);
        assertTrue(token.startsWith("eyJ"));
        verify(authRateLimitService).checkLoginIpLimit(CLIENT_IP);
        verify(authRateLimitService).clearLoginFailure("admin");
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);
        LoginRequest req = new LoginRequest(); req.setUsername("ghost"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, CLIENT_IP));
        assertEquals(401, ex.getErrorCode().getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(authRateLimitService).recordLoginFailure("ghost");
    }

    @Test
    void login_disabledUser_shouldReturnUnified401_noAccountLeak() {
        User u = new User(); u.setUsername("banned"); u.setStatus(0);
        when(userMapper.findByUsername("banned")).thenReturn(u);
        LoginRequest req = new LoginRequest(); req.setUsername("banned"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, CLIENT_IP));
        assertEquals(401, ex.getErrorCode().getCode());
        assertEquals("用户名或密码错误", ex.getMessage(), "禁用状态不得泄露（不得出现「账户已禁用」文案）");
        verify(authRateLimitService).recordLoginFailure("banned");
    }

    @Test
    void login_shouldThrowWhenWrongPassword() {
        User u = new User(); u.setStatus(1); u.setPassword("encoded");
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("wrong");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, CLIENT_IP));
        assertEquals(401, ex.getErrorCode().getCode());
        verify(authRateLimitService).recordLoginFailure("admin");
    }

    @Test
    void login_whenAccountLocked_shouldReturnUnified401() {
        doThrow(new BusinessException(dev.reboot.enums.ErrorCode.UNAUTHORIZED, "用户名或密码错误"))
                .when(authRateLimitService).checkUserLoginLocked("admin");
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, CLIENT_IP));
        assertEquals(401, ex.getErrorCode().getCode());
        verify(userMapper, never()).findByUsername(anyString());
    }

    @Test
    void login_ipLimited_shouldThrow429() {
        doThrow(new BusinessException(dev.reboot.enums.ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"))
                .when(authRateLimitService).checkLoginIpLimit(CLIENT_IP);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req, CLIENT_IP));
        assertEquals(429, ex.getErrorCode().getCode());
        verify(userMapper, never()).findByUsername(anyString());
    }

    @Test
    void register_shouldCreateUserAndAssignViewer() {
        RegisterRequest req = new RegisterRequest(); req.setUsername("newuser"); req.setPassword("p@ssw0rd");
        when(passwordEncoder.encode("p@ssw0rd")).thenReturn("encodedPw");
        UserVO vo = authService.register(req, CLIENT_IP);

        assertNotNull(vo);
        assertEquals("newuser", vo.getUsername());
        verify(authRateLimitService).checkRegisterIpLimit(CLIENT_IP);
        verify(userMapper).insert(any(User.class));
        verify(userRoleMapper).insert(any());
    }

    @Test
    void register_shouldThrowConflictOnDuplicate() {
        RegisterRequest req = new RegisterRequest(); req.setUsername("dup"); req.setPassword("123456");
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userMapper.insert(any())).thenThrow(new DuplicateKeyException("dup"));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req, CLIENT_IP));
        assertEquals(409, ex.getErrorCode().getCode());
    }

    @Test
    void register_ipLimited_shouldThrow429() {
        doThrow(new BusinessException(dev.reboot.enums.ErrorCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"))
                .when(authRateLimitService).checkRegisterIpLimit(CLIENT_IP);
        RegisterRequest req = new RegisterRequest(); req.setUsername("newuser"); req.setPassword("123456");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req, CLIENT_IP));
        assertEquals(429, ex.getErrorCode().getCode());
        verify(userMapper, never()).insert(any());
    }
}
