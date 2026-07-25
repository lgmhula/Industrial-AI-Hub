package dev.reboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录/注册请求 DTO。
 *
 * <p>所有字段均使用 Jakarta Validation 注解，由 {@code @Valid} 触发校验。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少6位")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
