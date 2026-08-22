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

    public AuthService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils,
                       AuthRateLimitService authRateLimitService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authRateLimitService = authRateLimitService;
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
            log.warn("登录失败：用户不存在 username={}", dto.getUsername());
            authRateLimitService.recordLoginFailure(dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            // 统一 401 文案，不泄露「账户已被禁用」这一存在性信息
            log.warn("登录失败：账户已禁用 username={}", dto.getUsername());
            authRateLimitService.recordLoginFailure(dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 username={}", dto.getUsername());
            authRateLimitService.recordLoginFailure(dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        authRateLimitService.clearLoginFailure(dto.getUsername());
        List<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        log.info("登录成功 username={} roles={}", dto.getUsername(), roles);
        return jwtUtils.generateToken(user.getId(), user.getUsername(), roles);
    }

    /**
     * 注册 —— 入口加固：IP 限流；创建用户并分配默认 VIEWER 角色（无站点成员 → 零资源访问权，P1-01）。
     *
     * @return UserVO（绝不包含 password 字段）
     * @throws BusinessException 用户名已存在 → 409；IP 超限 → 429
     */
    @Transactional
    public UserVO register(RegisterRequest dto, String clientIp) {
        authRateLimitService.checkRegisterIpLimit(clientIp);

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("注册失败：用户名已存在 username={}", dto.getUsername());
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(RoleEnum.VIEWER.getRoleId());
        userRoleMapper.insert(userRole);

        log.info("注册成功 username={} userId={}", dto.getUsername(), user.getId());
        return UserVO.from(user);
    }
}
