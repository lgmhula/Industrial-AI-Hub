package dev.reboot.service;

import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.entity.UserRole;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.util.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证业务逻辑层 —— 登录、注册、BCrypt 加密（P1-02-A-1 入口加固）。
 *
 * <p>安全行为（P1-02-A-1）：</p>
 * <ul>
 *   <li>所有认证失败（用户不存在/密码错误/账户禁用/锁定）统一返回 401「用户名或密码错误」，不泄露账号存在状态；</li>
 *   <li>IP 维度登录/注册限流（Redis 滑动窗口，超限 429）；</li>
 *   <li>账号维度失败计数（Redis，5 次失败锁定 15 分钟）。</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthRateLimitService authRateLimitService;
    private final TokenBlacklistService tokenBlacklistService;
    private final boolean registrationEnabled;
    private final String inviteCode;

    public AuthService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils,
                       AuthRateLimitService authRateLimitService,
                       TokenBlacklistService tokenBlacklistService,
                       @org.springframework.beans.factory.annotation.Value(
                               "${security.registration.enabled:false}") boolean registrationEnabled,
                       @org.springframework.beans.factory.annotation.Value(
                               "${security.registration.invite-code:}") String inviteCode) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authRateLimitService = authRateLimitService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.registrationEnabled = registrationEnabled;
        this.inviteCode = inviteCode;
    }

    /**
     * 登出（P1-02-A-4）：将当前 token（jti）加入黑名单，TTL = 剩余有效期。
     *
     * @param jti token 的 jti（由 JwtAuthFilter 注入 request attribute）
     * @param ttl 剩余有效时间（exp − now）
     */
    public void logout(String jti, java.time.Duration ttl) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        tokenBlacklistService.blacklistToken(jti, ttl);
        log.info("用户登出 jti={}", jti);
    }

    /**
     * 登录 —— 入口加固：IP 限流 → 账号锁定检查 → 统一 401 失败语义。
     *
     * @param dto      登录请求
     * @param clientIp 客户端 IP（Controller 从 request.getRemoteAddr() 取得，用于 IP 限流）
     * @throws BusinessException IP 超限 → 429；任何认证失败 → 401（统一文案）
     */
    public String login(LoginRequest dto, String clientIp) {
        authRateLimitService.checkLoginIpLimit(clientIp);
        authRateLimitService.checkUserLoginLocked(dto.getUsername());

        User user = userMapper.findByUsername(dto.getUsername());
        if (user == null) {
            // 不存在用户：仅 Redis 计数（无 DB 行可更新）
            log.warn("登录失败：用户不存在 username={}", dto.getUsername());
            authRateLimitService.recordLoginFailure(dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.isLockedNow()) {
            // DB 持久锁定：不泄露状态、不计数（锁定期间拒绝一切尝试）
            log.warn("登录失败：账户持久锁定 username={}", dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            // 统一 401 文案，不泄露「账户已被禁用」这一存在性信息
            log.warn("登录失败：账户已禁用 username={}", dto.getUsername());
            recordLoginFailure(user);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 username={}", dto.getUsername());
            recordLoginFailure(user);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 登录成功：清除 Redis 失败计数 + DB 安全状态
        authRateLimitService.clearLoginFailure(dto.getUsername());
        userMapper.resetLoginSecurity(user.getId());
        List<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        log.info("登录成功 username={} roles={}", dto.getUsername(), roles);
        return jwtUtils.generateToken(user.getId(), user.getUsername(), roles);
    }

    /**
     * 登录失败记录（双层）：Redis 快速计数（P1-02-A-1）+ DB 持久计数（P1-02-A-2）；
     * 连续失败达 {@link AuthRateLimitService#MAX_LOGIN_FAILURES} 次 → 持久锁定 15 分钟。
     */
    private void recordLoginFailure(User user) {
        authRateLimitService.recordLoginFailure(user.getUsername());
        int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        userMapper.updateFailedAttempts(user.getId(), attempts);
        if (attempts >= AuthRateLimitService.MAX_LOGIN_FAILURES) {
            userMapper.updateLockedUntil(user.getId(),
                    LocalDateTime.now().plus(AuthRateLimitService.LOGIN_FAIL_TTL));
        }
    }

    /**
     * 注册 —— 注册治理（P1-02-A-3）：
     * IP 限流 → 注册开关/邀请码校验（统一 403，不泄露开关与邀请码有效状态）
     * → 每日全局配额（429）→ 创建用户 + 默认 VIEWER 角色（不自动加入站点）。
     *
     * @return UserVO（绝不包含 password 字段）
     * @throws BusinessException 注册未开放/邀请码无效 → 403；配额超限/IP 超限 → 429；用户名重复 → 409（通用文案）
     */
    @Transactional
    public UserVO register(RegisterRequest dto, String clientIp) {
        authRateLimitService.checkRegisterIpLimit(clientIp);
        if (!registrationEnabled || !isInviteValid(dto.getInviteCode())) {
            // 统一失败语义：不泄露注册开关是否开启、邀请码是否正确
            throw new BusinessException(ErrorCode.FORBIDDEN, "注册失败，请稍后再试");
        }
        authRateLimitService.checkRegisterDailyQuota();

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("注册失败：用户名已存在 username={}", dto.getUsername());
            // 通用失败文案，不泄露用户名是否已被注册
            throw new BusinessException(ErrorCode.CONFLICT, "注册失败，请稍后再试");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(RoleEnum.VIEWER.getRoleId());
        userRoleMapper.insert(userRole);

        authRateLimitService.recordRegisterSuccess();
        log.info("注册成功 username={} userId={}", dto.getUsername(), user.getId());
        return UserVO.from(user);
    }

    /** 邀请码校验：配置了邀请码且与请求一致。 */
    private boolean isInviteValid(String code) {
        return inviteCode != null && !inviteCode.isBlank() && inviteCode.equals(code);
    }
}
