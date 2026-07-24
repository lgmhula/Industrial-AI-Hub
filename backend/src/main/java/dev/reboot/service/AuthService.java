package dev.reboot.service;

import dev.reboot.dto.LoginDTO;
import dev.reboot.dto.RegisterResponse;
import dev.reboot.entity.User;
import dev.reboot.entity.UserRole;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.util.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务逻辑层 —— 登录、注册、BCrypt 加密。
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
     * 登录 —— 验证用户名/密码，返回 JWT。
     *
     * @param dto 登录请求
     * @return JWT Token，null 表示用户名或密码错误
     */
    public String login(LoginDTO dto) {
        User user = userMapper.findByUsername(dto.getUsername());
        if (user == null) {
            log.warn("登录失败：用户不存在 username={}", dto.getUsername());
            return null;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败：账户已禁用 username={}", dto.getUsername());
            return null;
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 username={}", dto.getUsername());
            return null;
        }
        log.info("登录成功 username={}", dto.getUsername());
        return JwtUtils.generateToken(user.getId(), user.getUsername());
    }

    /**
     * 注册 —— 创建新用户，密码 BCrypt 加密，返回安全的 RegisterResponse。
     *
     * @param dto 注册请求
     * @return 注册成功响应，username 重复或数据库异常时返回 null
     */
    @Transactional
    public RegisterResponse register(LoginDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("注册失败：用户名已存在 username={}", dto.getUsername());
            return null;
        }

        // 分配默认 VIEWER 角色
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(3L); // VIEWER
        userRoleMapper.insert(userRole);

        log.info("注册成功 username={} userId={}", dto.getUsername(), user.getId());

        RegisterResponse resp = new RegisterResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setStatus(user.getStatus());
        resp.setCreatedAt(user.getCreatedAt());
        return resp;
    }
}
