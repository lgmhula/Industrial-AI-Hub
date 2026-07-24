package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User 业务逻辑层。
 *
 * @author hula0710
 * @since 2026-07-25
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 分页查询用户列表。
     *
     * @param page 页码 (1-based)
     * @param size 每页条数
     * @return PageInfo 包含分页元数据
     */
    public PageInfo<UserVO> listPage(int page, int size) {
        PageHelper.startPage(page, size);
        List<User> users = userMapper.findAll();
        List<UserVO> voList = users.stream().map(UserVO::from).toList();
        PageInfo<UserVO> pageInfo = new PageInfo<>(voList);
        // 手动设置 total（PageHelper 自动拦截，但需要拷贝）
        PageInfo<User> rawInfo = new PageInfo<>(users);
        pageInfo.setTotal(rawInfo.getTotal());
        return pageInfo;
    }

    /** 按 ID 查询，返回 UserVO。 */
    public UserVO getById(Long id) {
        User user = userMapper.findById(id);
        return user != null ? UserVO.from(user) : null;
    }

    /** 按用户名查询原始实体（内部调用）。 */
    public User getByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /** 编辑用户信息（email、phone）。 */
    public UserVO update(Long id, UserUpdateDTO dto) {
        User user = userMapper.findById(id);
        if (user == null) return null;
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        userMapper.update(user);
        log.info("用户信息更新 userId={}", id);
        return UserVO.from(user);
    }

    /** 切换用户启用/禁用状态。 */
    public boolean toggleStatus(Long id) {
        User user = userMapper.findById(id);
        if (user == null) return false;
        int newStatus = (user.getStatus() != null && user.getStatus() == 1) ? 0 : 1;
        userMapper.updateStatus(id, newStatus);
        log.info("用户状态切换 userId={} newStatus={}", id, newStatus);
        return true;
    }

    /** 删除用户。 */
    public boolean delete(Long id) {
        int rows = userMapper.deleteById(id);
        if (rows > 0) log.info("用户已删除 userId={}", id);
        return rows > 0;
    }
}
