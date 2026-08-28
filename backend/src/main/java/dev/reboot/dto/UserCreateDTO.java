package dev.reboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员创建用户的请求 DTO。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
public class UserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最长 64 字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度 6-128 字符")
    private String password;

    @Size(max = 128, message = "邮箱最长 128 字符")
    private String email;

    @Size(max = 20, message = "手机号最长 20 字符")
    private String phone;

    private List<Long> roleIds;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}
