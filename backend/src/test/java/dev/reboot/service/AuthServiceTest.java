package dev.reboot.service;

import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
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
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试 — 覆盖 JWT 认证全链路。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;

    @Test
    void login_shouldReturnTokenOnSuccess() {
        User u = new User(); u.setId(1L); u.setUsername("admin"); u.setPassword("encoded"); u.setStatus(1);
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(userRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));

        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("pass");
        String token = authService.login(req);

        assertNotNull(token);
        assertTrue(token.startsWith("eyJ"));
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);
        LoginRequest req = new LoginRequest(); req.setUsername("ghost"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(401, ex.getErrorCode().getCode());
    }

    @Test
    void login_shouldThrowWhenStatusZero() {
        User u = new User(); u.setStatus(0);
        when(userMapper.findByUsername("banned")).thenReturn(u);
        LoginRequest req = new LoginRequest(); req.setUsername("banned"); req.setPassword("x");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(403, ex.getErrorCode().getCode());
    }

    @Test
    void login_shouldThrowWhenWrongPassword() {
        User u = new User(); u.setStatus(1); u.setPassword("encoded");
        when(userMapper.findByUsername("admin")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        LoginRequest req = new LoginRequest(); req.setUsername("admin"); req.setPassword("wrong");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(401, ex.getErrorCode().getCode());
    }

    @Test
    void register_shouldCreateUserAndAssignViewer() {
        RegisterRequest req = new RegisterRequest(); req.setUsername("newuser"); req.setPassword("p@ssw0rd");
        when(passwordEncoder.encode("p@ssw0rd")).thenReturn("encodedPw");
        UserVO vo = authService.register(req);

        assertNotNull(vo);
        assertEquals("newuser", vo.getUsername());
        verify(userMapper).insert(any(User.class));
        verify(userRoleMapper).insert(any());
    }

    @Test
    void register_shouldThrowConflictOnDuplicate() {
        RegisterRequest req = new RegisterRequest(); req.setUsername("dup"); req.setPassword("123456");
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userMapper.insert(any())).thenThrow(new DuplicateKeyException("dup"));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(409, ex.getErrorCode().getCode());
    }
}
