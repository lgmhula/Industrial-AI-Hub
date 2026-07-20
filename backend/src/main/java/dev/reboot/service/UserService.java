package dev.reboot.service;

import dev.reboot.entity.User;
import dev.reboot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User 业务逻辑层。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public List<User> listAll() {
        return userMapper.findAll();
    }

    public User getById(Long id) {
        return userMapper.findById(id);
    }

    public User getByUsername(String username) {
        return userMapper.findByUsername(username);
    }
}
