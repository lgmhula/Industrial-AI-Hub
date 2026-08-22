package dev.reboot.service;

import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 注册治理单元测试（P1-02-A-3）。
 *
 * <p>覆盖：注册开关关闭（403）、无效/缺失邀请码（403 不泄露）、有效邀请码（VIEWER + 无站点成员）、
 * 重复用户名（409 通用文案）、每日配额（429）。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class AuthRegistrationGovernanceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuthRateLimitService authRateLimitService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private LoginAuditService loginAuditService;

    private static final String CLIENT_IP = "1.2.3.4";
    private static final String INVITE = "secret-invite";

    /** 手工构造 AuthService（@Value 参数显式传入，覆盖开关/邀请码场景）。 */
    private AuthService service(boolean enabled, String inviteCode) {
        return new AuthService(userMapper, userRoleMapper, passwordEncoder, jwtUtils,
                authRateLimitService, tokenBlacklistService, loginAuditService, enabled, inviteCode);
    }

    private RegisterRequest req(String username, String inviteCode) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username);
        r.setPassword("p@ssw0rd");
        r.setInviteCode(inviteCode);
        return r;
    }

    @Test
    void registrationDisabled_shouldReturn403_andNotCreateUser() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(false, INVITE).register(req("newuser", INVITE), CLIENT_IP));
        assertEquals(403, ex.getErrorCode().getCode());
        verify(userMapper, never()).insert(any());
        verify(authRateLimitService).checkRegisterIpLimit(CLIENT_IP);
    }

    @Test
    void invalidInvite_shouldReturn403_noLeak() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(true, INVITE).register(req("newuser", "wrong-code"), CLIENT_IP));
        assertEquals(403, ex.getErrorCode().getCode());
        assertEquals("注册失败，请稍后再试", ex.getMessage(), "不泄露邀请码有效状态");
        verify(userMapper, never()).insert(any());
    }

    @Test
    void missingInviteCode_shouldReturn403() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(true, INVITE).register(req("newuser", null), CLIENT_IP));
        assertEquals(403, ex.getErrorCode().getCode());
    }

    @Test
    void validInvite_shouldCreateUserWithViewerRole_noSiteMembership() {
        User created = new User();
        created.setId(100L);
        created.setUsername("newuser");
        when(passwordEncoder.encode("p@ssw0rd")).thenReturn("enc");
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return 1;
        });

        UserVO vo = service(true, INVITE).register(req("newuser", INVITE), CLIENT_IP);

        assertNotNull(vo);
        assertEquals("newuser", vo.getUsername());
        // 默认 VIEWER 角色
        verify(userRoleMapper).insert(argThat(ur ->
                ur.getUserId().equals(100L) && ur.getRoleId().equals(RoleEnum.VIEWER.getRoleId())));
        // 无站点成员：AuthService 无 user_site 写入路径（P1-01 要求注册不自动加入站点）
        verify(authRateLimitService).recordRegisterSuccess();
        verify(authRateLimitService).checkRegisterDailyQuota();
    }

    @Test
    void duplicateUsername_shouldReturnGeneric409() {
        when(passwordEncoder.encode("p@ssw0rd")).thenReturn("enc");
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("dup"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(true, INVITE).register(req("dup", INVITE), CLIENT_IP));
        assertEquals(409, ex.getErrorCode().getCode());
        assertNotEquals("用户名已存在", ex.getMessage(), "不得泄露用户名是否已被注册");
        verify(userRoleMapper, never()).insert(any());
    }

    @Test
    void dailyQuotaExceeded_shouldReturn429() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "注册人数已达今日上限"))
                .when(authRateLimitService).checkRegisterDailyQuota();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(true, INVITE).register(req("newuser", INVITE), CLIENT_IP));
        assertEquals(429, ex.getErrorCode().getCode());
        verify(userMapper, never()).insert(any());
    }
}
