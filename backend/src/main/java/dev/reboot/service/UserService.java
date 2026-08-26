package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.dto.UserCreateDTO;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.entity.Role;
import dev.reboot.entity.User;
import dev.reboot.entity.UserRole;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.RoleMapper;
import dev.reboot.mapper.UserMapper;
import dev.reboot.mapper.UserRoleMapper;
import dev.reboot.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final RoleMapper roleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthRateLimitService authRateLimitService;
    private final TokenBlacklistService tokenBlacklistService;

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                       BCryptPasswordEncoder passwordEncoder,
                       AuthRateLimitService authRateLimitService,
                       TokenBlacklistService tokenBlacklistService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.authRateLimitService = authRateLimitService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /** 分页查询用户列表（可选关键字搜索），批量填充角色编码。 */
    public PageInfo<UserVO> listPage(int page, int size, String keyword) {
        PageHelper.startPage(page, size);
        List<User> users = (keyword != null && !keyword.isBlank())
                ? userMapper.search(keyword.trim())
                : userMapper.findAll();
        PageInfo<User> rawPageInfo = new PageInfo<>(users);
        List<UserVO> voList = users.stream().map(UserVO::from).toList();

        if (!voList.isEmpty()) {
            List<Long> userIds = voList.stream().map(UserVO::getId).toList();
            List<Map<String, Object>> rows = userRoleMapper.findRoleCodesByUserIds(userIds);
            Map<Long, List<String>> roleMap = rows.stream().collect(
                    Collectors.groupingBy(
                            r -> ((Number) r.get("user_id")).longValue(),
                            Collectors.mapping(r -> (String) r.get("role_code"), Collectors.toList())
                    )
            );
            voList.forEach(vo -> vo.setRoleCodes(roleMap.getOrDefault(vo.getId(), List.of())));
        }

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
     * 按 ID 查询（Spring Cache 注解缓存），返回 UserVO。
     *
     * @throws BusinessException 用户不存在 → 404
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
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
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
    public Integer toggleStatus(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        int newStatus = (user.getStatus() != null && user.getStatus() == 1) ? 0 : 1;
        userMapper.updateStatus(id, newStatus);
        // P1-02-A-4：禁用（1→0）时撤销该用户全部存量 token；恢复启用不撤销历史 token
        if (newStatus == 0) {
            tokenBlacklistService.revokeUser(id);
        }
        log.info("用户状态切换 userId={} newStatus={}", id, newStatus);
        return newStatus;
    }

    /**
     * 逻辑删除用户及关联的 user_role 记录。
     *
     * @param currentUserId 当前登录用户 ID（来自 JWT）；禁止删除自己，避免系统无管理员
     * @throws BusinessException 删除当前登录用户 → 400
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
    public boolean delete(Long id, Long currentUserId) {
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能删除当前登录用户");
        }
        User user = userMapper.findById(id);
        if (user == null) {
            return false;
        }
        userRoleMapper.deleteByUserId(id);
        int rows = userMapper.softDeleteById(id);
        if (rows > 0) {
            log.info("用户已逻辑删除 userId={}", id);
        }
        return rows > 0;
    }

    /**
     * 修改密码。
     *
     * @throws BusinessException 用户不存在 → 404；旧密码错误 → 401；新密码不合法 → 400
     */
    public boolean changePassword(Long id, String oldPassword, String newPassword) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "旧密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码长度至少6位");
        }
        String encoded = passwordEncoder.encode(newPassword);
        int rows = userMapper.updatePassword(id, encoded);
        if (rows > 0) {
            // P1-02-A-4：记录改密时间（V5 列，旧 token 失效基准）+ 撤销全部存量 token
            userMapper.updatePasswordChangedAt(id, LocalDateTime.now());
            tokenBlacklistService.revokeUser(id);
        }
        log.info("密码已更新 userId={}", id);
        return rows > 0;
    }

    /**
     * 管理员锁定用户（P1-02-A-2）：设置持久锁定 {@code locked_until = now + 15min}。
     * 锁定期间登录返回统一 401（{@link dev.reboot.service.AuthService#login} 校验 DB 锁定）。
     *
     * @return 用户不存在 → false
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
    public boolean lockUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            return false;
        }
        userMapper.updateLockedUntil(id, LocalDateTime.now().plus(AuthRateLimitService.LOGIN_FAIL_TTL));
        log.info("用户被管理员锁定 userId={}", id);
        return true;
    }

    /**
     * 管理员解锁用户（P1-02-A-2）：清除 DB 安全状态（failed_attempts=0, locked_until=NULL）
     * 并同步删除 Redis 失败计数。
     *
     * @return 用户不存在 → false
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
    public boolean unlockUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            return false;
        }
        userMapper.resetLoginSecurity(id);
        authRateLimitService.clearLoginFailure(user.getUsername());
        log.info("用户被管理员解锁 userId={}", id);
        return true;
    }

    /**
     * 管理员创建用户（后台管理，非注册流程）。
     *
     * @throws BusinessException 用户名已存在 → 409
     */
    @Transactional
    public UserVO createUser(UserCreateDTO dto) {
        if (userMapper.findByUsername(dto.getUsername()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setStatus(1);
        userMapper.insert(user);
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            for (Long roleId : dto.getRoleIds()) {
                UserRole ur = new UserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        log.info("管理员创建用户 userId={} username={}", user.getId(), user.getUsername());
        return UserVO.from(user);
    }

    /**
     * 管理员重置用户密码（无需旧密码）。
     *
     * @throws BusinessException 用户不存在 → 404；新密码不合法 → 400
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#id")
    public void adminResetPassword(Long id, String newPassword) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码长度至少6位");
        }
        String encoded = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(id, encoded);
        userMapper.updatePasswordChangedAt(id, LocalDateTime.now());
        userMapper.resetLoginSecurity(id);
        authRateLimitService.clearLoginFailure(user.getUsername());
        tokenBlacklistService.revokeUser(id);
        log.info("管理员重置用户密码 userId={}", id);
    }

    /**
     * 给用户分配角色。
     *
     * @throws BusinessException 用户不存在 → 404；角色不存在 → 404
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#userId")
    public void assignRole(Long userId, Long roleId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Role role = roleMapper.findById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        List<UserRole> existing = userRoleMapper.findByUserId(userId);
        boolean already = existing.stream().anyMatch(ur -> ur.getRoleId().equals(roleId));
        if (already) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户已拥有该角色");
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
        log.info("角色已分配 userId={} roleId={}", userId, roleId);
    }

    /**
     * 取消用户的角色。
     *
     * @throws BusinessException 用户不存在 → 404
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_USER_DETAIL, key = "#userId")
    public void revokeRole(Long userId, Long roleId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        int rows = userRoleMapper.deleteByUserAndRole(userId, roleId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户未拥有该角色");
        }
        log.info("角色已取消 userId={} roleId={}", userId, roleId);
    }

    /** 查询用户的角色编码列表。 */
    public List<String> getUserRoleCodes(Long userId) {
        return userRoleMapper.findRoleCodesByUserId(userId);
    }
}