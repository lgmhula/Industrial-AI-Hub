package dev.reboot.controller;

import com.github.pagehelper.PageInfo;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.UserUpdateDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 用户管理 REST 控制器 —— 管理员专属。
 *
 * <p>业务异常统一由 {@link dev.reboot.exception.GlobalExceptionHandler} 处理。</p>
 *
 * @author hula0710
 * @since 2026-07-25
 */
@RestController
@RequestMapping("/api/users")
@RequireRole(RoleEnum.ADMIN)
@Tag(name = "02-用户管理", description = "管理员专属 — 用户 CRUD + 状态切换")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 分页查询用户列表。 */
    @GetMapping
    @Operation(summary = "分页查询用户")
    public ApiResponse<PageInfo<UserVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(userService.listPage(page, size));
    }

    /** 按 ID 查询用户。 */
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询用户")
    public ApiResponse<UserVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    /** 编辑用户信息（email、phone）。 */
    @PutMapping("/{id}")
    @Operation(summary = "编辑用户信息（email/phone）")
    public ApiResponse<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        return ApiResponse.ok("用户信息更新成功", userService.update(id, dto));
    }

    /**
     * 切换用户启用/禁用状态。
     *
     * @return 新状态值（1=启用, 0=禁用）
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "切换用户启用/禁用")
    public ApiResponse<Integer> toggleStatus(@PathVariable Long id) {
        return ApiResponse.ok("状态更新成功", userService.toggleStatus(id));
    }

    /** 逻辑删除用户（禁止删除当前登录用户，防止误删自身导致系统无管理员）。 */
    @DeleteMapping("/{id}")
    @Operation(summary = "逻辑删除用户")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Object currentUserId = request.getAttribute("userId");
        if (currentUserId != null && Long.valueOf(currentUserId.toString()).equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能删除当前登录用户");
        }
        userService.delete(id);
        return ApiResponse.ok("用户已删除", null);
    }
}
