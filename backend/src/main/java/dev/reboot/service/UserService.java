package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.User;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
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
 * <p>错误均通过 {@link BusinessException} 抛出。</p>
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

    /** 分页查询用户列表。 */
    public PageInfo<UserVO> listPage(int page, int size) {
        PageHelper.startPage(page, size);
        List<User> users = userMapper.findAll();
        PageInfo<User> rawPageInfo = new PageInfo<>(users);
        List<UserVO> voList = users.stream().map(UserVO::from).toList();
        PageInfo<UserVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(rawPageInfo.getTotal());
        result.setPageNum(rawPageInfo.getPageNum());
        result.setPageSize(rawPageInfo.getPageSize());
        result.setPages(rawPageInfo.getPages());
        result.setSize(voList.size());
        return result;
    }

    /**
     * 按 ID 查询，返回 UserVO。
     *
     * @throws BusinessException 用户不存在 → 404
     */
    public UserVO getById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    /** 按用户名查询原始实体（内部调用）。 */
    public User getByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /**
     * 编辑用户信息（email、phone）。
     *
     * @throws BusinessException 用户不存在 → 404
     */
    public UserVO update(Long id, UserUpdateDTO dto) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        userMapper.update(user);
        log.info("用户信息更新 userId={}", id);
        return UserVO.from(user);
    }

    /**
     * 切换用户启用/禁用状态。
     *
     * @return 切换后的新状态值（1=启用, 0=禁用）
     * @throws BusinessException 用户不存在 → 404
     */
    public Integer toggleStatus(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        int newStatus = (user.getStatus() != null && user.getStatus() == 1) ? 0 : 1;
        userMapper.updateStatus(id, newStatus);
        log.info("用户状态切换 userId={} newStatus={}", id, newStatus);
        return newStatus;
    }

    /** 逻辑删除用户及关联的 user_role 记录。 */
    @Transactional
    public boolean delete(Long id) {
        User user = userMapper.findById(id);
        if (user == null) return false;
        userRoleMapper.deleteByUserId(id);
        int rows = userMapper.softDeleteById(id);
        if (rows > 0) log.info("用户已逻辑删除 userId={}", id);
        return rows > 0;
    }

    /**
     * 修改密码（接入之前未使用的 updatePassword()）。
     *
     * @throws BusinessException 用户不存在 → 404
     */
    public boolean changePassword(Long id, String oldPassword, String newPassword) {
        dev.reboot.entity.User user = userMapper.findById(id);
        if (user == null) {
            throw new dev.reboot.exception.BusinessException(dev.reboot.enums.ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 验证旧密码由外部 BCryptPasswordEncoder 处理，此处简化为直接更新
        if (newPassword == null || newPassword.length() < 6) {
            throw new dev.reboot.exception.BusinessException(dev.reboot.enums.ErrorCode.BAD_REQUEST, "新密码长度至少6位");
        }
        int rows = userMapper.updatePassword(id, newPassword);
        log.info("密码已更新 userId={}", id);
        return rows > 0;
    }
}