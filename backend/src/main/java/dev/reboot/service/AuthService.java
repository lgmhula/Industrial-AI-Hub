package dev.reboot.service;

import dev.reboot.dto.LoginDTO;
import dev.reboot.entity.User;
import dev.reboot.mapper.UserMapper;
import dev.reboot.util.JwtUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务逻辑层 —— 登录、注册、BCrypt 加密。
 *
 * @author hula0710
 * @since 2026-07-21
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
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
        if (user == null) return null;
        if (user.getStatus() != null && user.getStatus() == 0) return null; // 禁用用户

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            return null;
        }

        return JwtUtils.generateToken(user.getId(), user.getUsername());
    }

    /**
     * 注册 —— 创建新用户，密码 BCrypt 加密。
     *
     * @param dto 注册请求（username + password）
     * @return 新创建的用户（不含密码字段），null 表示用户名已存在
     */
    @Transactional
    public User register(LoginDTO dto) {
        // 检查用户名是否已存在
        if (userMapper.findByUsername(dto.getUsername()) != null) {
            return null;
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        // 安全起见，返回前清空密码
        user.setPassword(null);
        return user;
    }
}
