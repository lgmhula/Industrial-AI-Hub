package dev.reboot.service;

import com.github.pagehelper.PageInfo;
import dev.reboot.dto.UserUpdateDTO;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试。
 *
 * <p>Mock UserMapper / UserRoleMapper / BCryptPasswordEncoder，
 * 验证所有公开方法的正常与异常路径。</p>
 *
 * @author hula0710
 * @since 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private AuthRateLimitService authRateLimitService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;


    @InjectMocks
    private UserService userService;

    /* ---- helpers ---- */

    private User newUser(Long id, String username, Integer status, String password) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPhone("13800138000");
        u.setStatus(status);
        u.setPassword(password);
        u.setIsDeleted(0);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    /* ==============================
     * listPage
     * ============================== */

    @Test
    void listPage_shouldReturnPagedUsers() {
        User u1 = newUser(1L, "alice", 1, "pw1");
        User u2 = newUser(2L, "bob", 1, "pw2");
        when(userMapper.findAll()).thenReturn(Arrays.asList(u1, u2));

        PageInfo<UserVO> result = userService.listPage(1, 10);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("alice", result.getList().get(0).getUsername());
        // password field intentionally absent from UserVO
    }

    @Test
    void listPage_shouldReturnEmptyWhenNoUsers() {
        when(userMapper.findAll()).thenReturn(Collections.emptyList());

        PageInfo<UserVO> result = userService.listPage(1, 10);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    /* ==============================
     * getById
     * ============================== */

    @Test
    void getById_shouldReturnUserVO() {
        User u = newUser(1L, "alice", 1, "secret");
        when(userMapper.findById(1L)).thenReturn(u);

        UserVO vo = userService.getById(1L);

        assertEquals("alice", vo.getUsername());
        // password field intentionally absent from UserVO
    }

    @Test
    void getById_shouldThrowNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getById(99L));
        assertEquals(404, ex.getErrorCode().getCode());
    }

    /* ==============================
     * getByUsername
     * ============================== */

    @Test
    void getByUsername_shouldReturnUser() {
        User u = newUser(1L, "alice", 1, "pw");
        when(userMapper.findByUsername("alice")).thenReturn(u);

        User result = userService.getByUsername("alice");
        assertNotNull(result);
        assertEquals("alice", result.getUsername());
    }

    @Test
    void getByUsername_shouldReturnNullForUnknown() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);
        assertNull(userService.getByUsername("ghost"));
    }

    /* ==============================
     * update
     * ============================== */

    @Test
    void update_shouldUpdateEmailAndPhone() {
        User u = newUser(1L, "alice", 1, "pw");
        when(userMapper.findById(1L)).thenReturn(u);

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setEmail("new@test.com");
        dto.setPhone("13900139000");

        UserVO vo = userService.update(1L, dto);

        verify(userMapper).update(u);
        assertEquals("new@test.com", vo.getEmail());
        assertEquals("13900139000", vo.getPhone());
    }

    @Test
    void update_shouldThrowNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);
        UserUpdateDTO dto = new UserUpdateDTO();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(99L, dto));
        assertEquals(404, ex.getErrorCode().getCode());
    }

    /* ==============================
     * toggleStatus
     * ============================== */

    @Test
    void toggleStatus_shouldDisableActiveUser() {
        User u = newUser(1L, "alice", 1, "pw");
        when(userMapper.findById(1L)).thenReturn(u);

        Integer newStatus = userService.toggleStatus(1L);

        assertEquals(0, newStatus);
        verify(userMapper).updateStatus(1L, 0);
    }

    @Test
    void toggleStatus_shouldEnableInactiveUser() {
        User u = newUser(2L, "bob", 0, "pw");
        when(userMapper.findById(2L)).thenReturn(u);

        Integer newStatus = userService.toggleStatus(2L);

        assertEquals(1, newStatus);
        verify(userMapper).updateStatus(2L, 1);
    }

    @Test
    void toggleStatus_nullStatus_shouldEnable() {
        User u = newUser(3L, "carol", null, "pw");
        when(userMapper.findById(3L)).thenReturn(u);

        Integer newStatus = userService.toggleStatus(3L);

        assertEquals(1, newStatus);
    }

    @Test
    void toggleStatus_shouldThrowNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userService.toggleStatus(99L));
    }

    /* ==============================
     * delete (soft)
     * ============================== */

    @Test
    void delete_shouldSoftDeleteUserAndRoles() {
        User u = newUser(1L, "alice", 1, "pw");
        when(userMapper.findById(1L)).thenReturn(u);
        when(userMapper.softDeleteById(1L)).thenReturn(1);

        boolean deleted = userService.delete(1L, 99L);

        assertTrue(deleted);
        verify(userRoleMapper).deleteByUserId(1L);
        verify(userMapper).softDeleteById(1L);
    }

    @Test
    void delete_shouldReturnFalseWhenNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);

        assertFalse(userService.delete(99L, 98L));
        verify(userMapper, never()).softDeleteById(anyLong());
    }

    @Test
    void delete_shouldRejectDeletingSelf() {
        // 守卫在 findById 之前抛出，无需 stub findById
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(1L, 1L));

        assertEquals(400, ex.getErrorCode().getCode());
        assertTrue(ex.getMessage().contains("不能删除当前登录用户"));
        verify(userMapper, never()).softDeleteById(anyLong());
    }

    /* ==============================
     * changePassword
     * ============================== */

    @Test
    void changePassword_shouldSucceed() {
        User u = newUser(1L, "alice", 1, "oldEncoded");
        when(userMapper.findById(1L)).thenReturn(u);
        when(passwordEncoder.matches("oldPlain", "oldEncoded")).thenReturn(true);
        when(passwordEncoder.encode("newPlain")).thenReturn("newEncoded");
        when(userMapper.updatePassword(1L, "newEncoded")).thenReturn(1);

        assertTrue(userService.changePassword(1L, "oldPlain", "newPlain"));
        verify(userMapper).updatePassword(1L, "newEncoded");
    }

    @Test
    void changePassword_shouldThrowWrongOldPassword() {
        User u = newUser(1L, "alice", 1, "oldEncoded");
        when(userMapper.findById(1L)).thenReturn(u);
        when(passwordEncoder.matches("wrong", "oldEncoded")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, "wrong", "new"));
        assertEquals(401, ex.getErrorCode().getCode());
    }

    @Test
    void changePassword_shouldThrowWhenNewPasswordTooShort() {
        User u = newUser(1L, "alice", 1, "oldEncoded");
        when(userMapper.findById(1L)).thenReturn(u);
        when(passwordEncoder.matches("oldPlain", "oldEncoded")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, "oldPlain", "12345"));
        assertEquals(400, ex.getErrorCode().getCode());
    }

    @Test
    void changePassword_shouldThrowNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userService.changePassword(99L, "old", "newPwd"));
    }

    @Test
    void changePassword_nullOldPassword_shouldThrow() {
        User u = newUser(1L, "alice", 1, "oldEncoded");
        when(userMapper.findById(1L)).thenReturn(u);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, null, "newPwd"));
        assertEquals(401, ex.getErrorCode().getCode());
    }

    /* ============ P1-02-A-2 管理员锁定/解锁 ============ */

    @Test
    void lockUser_shouldSetPersistentLock() {
        when(userMapper.findById(1L)).thenReturn(newUser(1L, "alice", 1, "pw"));
        assertTrue(userService.lockUser(1L));
        verify(userMapper).updateLockedUntil(eq(1L), notNull());
    }

    @Test
    void lockUser_shouldReturnFalseWhenNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);
        assertFalse(userService.lockUser(99L));
        verify(userMapper, never()).updateLockedUntil(anyLong(), any());
    }

    @Test
    void unlockUser_shouldClearDbAndRedis() {
        when(userMapper.findById(1L)).thenReturn(newUser(1L, "alice", 1, "pw"));
        assertTrue(userService.unlockUser(1L));
        verify(userMapper).resetLoginSecurity(1L);
        verify(authRateLimitService).clearLoginFailure("alice");
    }

    @Test
    void unlockUser_shouldReturnFalseWhenNotFound() {
        when(userMapper.findById(99L)).thenReturn(null);
        assertFalse(userService.unlockUser(99L));
        verify(userMapper, never()).resetLoginSecurity(anyLong());
    }

    /* ============ P1-02-A-4 状态联动撤销 ============ */

    @Test
    void toggleStatus_disable_shouldRevokeUserTokens() {
        when(userMapper.findById(1L)).thenReturn(newUser(1L, "alice", 1, "pw"));
        when(userMapper.updateStatus(1L, 0)).thenReturn(1);
        Integer newStatus = userService.toggleStatus(1L);
        assertEquals(0, newStatus);
        verify(tokenBlacklistService).revokeUser(1L);
    }

    @Test
    void toggleStatus_enable_shouldNotRevoke() {
        when(userMapper.findById(1L)).thenReturn(newUser(1L, "alice", 0, "pw"));
        when(userMapper.updateStatus(1L, 1)).thenReturn(1);
        Integer newStatus = userService.toggleStatus(1L);
        assertEquals(1, newStatus);
        verify(tokenBlacklistService, never()).revokeUser(anyLong());
    }

    @Test
    void changePassword_shouldRecordChangedAtAndRevoke() {
        when(userMapper.findById(1L)).thenReturn(newUser(1L, "alice", 1, "oldEnc"));
        when(passwordEncoder.matches("old", "oldEnc")).thenReturn(true);
        when(passwordEncoder.encode("newPwd6")).thenReturn("newEnc");
        when(userMapper.updatePassword(1L, "newEnc")).thenReturn(1);

        assertTrue(userService.changePassword(1L, "old", "newPwd6"));
        verify(userMapper).updatePasswordChangedAt(eq(1L), notNull());
        verify(tokenBlacklistService).revokeUser(1L);
    }
}
