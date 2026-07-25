package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRoleMapper userRoleMapper;

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 分页查询用户列表。
     *
     * <p>先通过 PageHelper 分页获取原始数据，捕获分页元数据后再映射为 UserVO。</p>
     *
     * @param page 页码 (1-based)
     * @param size 每页条数
     * @return PageInfo 包含正确的 pageNum/pages/pageSize/total
     */
    public PageInfo<UserVO> listPage(int page, int size) {
        PageHelper.startPage(page, size);
        List<User> users = userMapper.findAll();
        // 从 Page 对象捕获分页元数据（PageHelper 返回的 List 实际是 Page 实例）
        PageInfo<User> rawPageInfo = new PageInfo<>(users);
        // 映射为 VO
        List<UserVO> voList = users.stream().map(UserVO::from).toList();
        // 构造结果并注入正确的分页元数据
        PageInfo<UserVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(rawPageInfo.getTotal());
        result.setPageNum(rawPageInfo.getPageNum());
        result.setPageSize(rawPageInfo.getPageSize());
        result.setPages(rawPageInfo.getPages());
        result.setSize(voList.size());
        return result;
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

    /**
     * 切换用户启用/禁用状态。
     *
     * @return 切换后的新状态值（1=启用, 0=禁用），用户不存在时返回 null
     */
    public Integer toggleStatus(Long id) {
        User user = userMapper.findById(id);
        if (user == null) return null;
        int newStatus = (user.getStatus() != null && user.getStatus() == 1) ? 0 : 1;
        userMapper.updateStatus(id, newStatus);
        log.info("用户状态切换 userId={} newStatus={}", id, newStatus);
        return newStatus;
    }

    /**
     * 逻辑删除用户及关联的 user_role 记录。
     *
     * <p>与 Device 保持一致：逻辑删除而非物理删除。</p>
     */
    @Transactional
    public boolean delete(Long id) {
        User user = userMapper.findById(id);
        if (user == null) return false;
        userRoleMapper.deleteByUserId(id);
        int rows = userMapper.softDeleteById(id);
        if (rows > 0) log.info("用户已逻辑删除 userId={}", id);
        return rows > 0;
    }
}
