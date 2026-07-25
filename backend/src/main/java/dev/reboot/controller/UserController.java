package dev.reboot.controller;

import com.github.pagehelper.PageInfo;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理 REST 控制器 —— 管理员专属。
 *
 * @author hula0710
 * @since 2026-07-25
 */
@RestController
@RequestMapping("/api/users")
@RequireRole(RoleEnum.ADMIN)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户列表。
     *
     * <p>GET /api/users?page=1&size=10</p>
     */
    @GetMapping
    public ApiResponse<PageInfo<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(userService.listPage(page, size));
    }

    /**
     * 按 ID 查询用户。
     */
    @GetMapping("/{id}")
    public ApiResponse<UserVO> getById(@PathVariable Long id) {
        UserVO vo = userService.getById(id);
        if (vo == null) return ApiResponse.error(404, "用户不存在");
        return ApiResponse.ok(vo);
    }

    /**
     * 编辑用户信息（email、phone）。
     */
    @PutMapping("/{id}")
    public ApiResponse<UserVO> update(@PathVariable Long id, @RequestBody UserUpdateDTO dto) {
        UserVO vo = userService.update(id, dto);
        if (vo == null) return ApiResponse.error(404, "用户不存在");
        return ApiResponse.ok("用户信息更新成功", vo);
    }

    /**
     * 切换用户启用/禁用状态。
     *
     * <p>1 (启用) ↔ 0 (禁用)。返回切换后的新状态值。</p>
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Integer> toggleStatus(@PathVariable Long id) {
        Integer newStatus = userService.toggleStatus(id);
        if (newStatus == null) return ApiResponse.error(404, "用户不存在");
        return ApiResponse.ok(newStatus == 1 ? "用户已启用" : "用户已禁用", newStatus);
    }

    /**
     * 删除用户（逻辑删除）。
     *
     * <p>管理员不可删除自己。删除前清理 user_role 关联记录。</p>
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId != null && currentUserId.equals(id)) {
            return ApiResponse.error(400, "不能删除当前登录用户");
        }
        if (userService.delete(id)) return ApiResponse.ok(null);
        return ApiResponse.error(404, "用户不存在");
    }
}
