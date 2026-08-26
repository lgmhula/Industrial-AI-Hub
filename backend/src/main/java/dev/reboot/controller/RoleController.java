package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.RoleDTO;
import dev.reboot.dto.RoleVO;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理 REST 控制器 — 管理员专属。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@RestController
@RequestMapping("/api/roles")
@RequireRole(RoleEnum.ADMIN)
@Tag(name = "06-角色管理", description = "管理员专属 — 角色 CRUD + 启用/禁用")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "角色列表")
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.ok(roleService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "角色详情")
    public ApiResponse<RoleVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(roleService.findById(id));
    }

    @OperationLog(operationType = "CREATE", targetType = "ROLE", description = "创建角色")
    @PostMapping
    @Operation(summary = "创建角色")
    public ApiResponse<RoleVO> create(@Valid @RequestBody RoleDTO dto) {
        return ApiResponse.ok("角色创建成功", roleService.create(dto));
    }

    @OperationLog(operationType = "UPDATE", targetType = "ROLE", description = "更新角色 {0}")
    @PutMapping("/{id}")
    @Operation(summary = "更新角色")
    public ApiResponse<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleDTO dto) {
        return ApiResponse.ok("角色更新成功", roleService.update(id, dto));
    }

    @OperationLog(operationType = "DELETE", targetType = "ROLE", description = "删除角色 {0}")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色（逻辑删除）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok("角色已删除", null);
    }

    @OperationLog(operationType = "UPDATE", targetType = "ROLE", description = "切换角色状态 {0}")
    @PutMapping("/{id}/status")
    @Operation(summary = "切换角色启用/禁用")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id) {
        roleService.toggleStatus(id);
        return ApiResponse.ok("状态已更新", null);
    }
}
