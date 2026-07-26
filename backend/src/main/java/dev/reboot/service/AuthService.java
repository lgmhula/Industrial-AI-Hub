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
 * 认证业务逻辑层 —— 登录、注册、BCrypt 加密。
 *
 * <p>错误均通过 {@link BusinessException} 抛出，
 * 由 {@link dev.reboot.exception.GlobalExceptionHandler} 统一转为 ApiResponse。</p>
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

    public AuthService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                       BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录 —— 验证用户名/密码，返回包含角色信息的 JWT。
     *
     * @throws BusinessException 用户名不存在/密码错误 → 401；账户已禁用 → 403
     */
    public String login(LoginRequest dto) {
        User user = userMapper.findByUsername(dto.getUsername());
        if (user == null) {
            log.warn("登录失败：用户不存在 username={}", dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败：账户已禁用 username={}", dto.getUsername());
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 username={}", dto.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        List<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        log.info("登录成功 username={} roles={}", dto.getUsername(), roles);
        return JwtUtils.generateToken(user.getId(), user.getUsername(), roles);
    }

    /**
     * 注册 —— 创建用户，分配默认 VIEWER 角色。
     *
     * @return UserVO（绝不包含 password 字段）
     * @throws BusinessException 用户名已存在 → 409
     */
    @Transactional
    public UserVO register(RegisterRequest dto) {
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
