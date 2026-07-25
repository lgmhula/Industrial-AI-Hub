package dev.reboot.service;

import dev.reboot.dto.LoginDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.entity.UserRole;
import dev.reboot.enums.RoleEnum;
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

        List<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        log.info("登录成功 username={} roles={}", dto.getUsername(), roles);
        return JwtUtils.generateToken(user.getId(), user.getUsername(), roles);
    }

    /**
     * 注册 —— 创建用户，分配默认 VIEWER 角色。
     *
     * <p>返回 UserVO（绝不包含 password 字段）。</p>
     */
    @Transactional
    public UserVO register(LoginDTO dto) {
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

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(RoleEnum.VIEWER.getRoleId());
        userRoleMapper.insert(userRole);

        log.info("注册成功 username={} userId={}", dto.getUsername(), user.getId());
        return UserVO.from(user);
    }
}
