package dev.reboot.service;

import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User 业务逻辑层。
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 查询所有用户，返回 UserVO（不含密码）。 */
    public List<UserVO> listAll() {
        return userMapper.findAll().stream()
                .map(UserVO::from)
                .collect(Collectors.toList());
    }

    /** 按 ID 查询，返回 UserVO。 */
    public UserVO getById(Long id) {
        User user = userMapper.findById(id);
        return user != null ? UserVO.from(user) : null;
    }

    /** 按用户名查询原始实体（内部调用，含密码）。 */
    public User getByUsername(String username) {
        return userMapper.findByUsername(username);
    }
}
