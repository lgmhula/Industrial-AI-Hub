package dev.reboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 角色数据传输对象 — 用于创建/更新角色。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
public class RoleDTO {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称最长 64 字符")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 32, message = "角色编码最长 32 字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码须为大写字母开头的大写字母+数字+下划线")
    private String roleCode;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;

    private Integer status;

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
