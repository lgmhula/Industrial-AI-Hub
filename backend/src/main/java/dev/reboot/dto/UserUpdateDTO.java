package dev.reboot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * 用户编辑请求 DTO —— 管理员编辑用户信息时使用。
 *
 * <p>校验规则：</p>
 * <ul>
 *   <li>email — 可选，若填写必须符合邮箱格式</li>
 *   <li>phone — 可选，若填写必须是 11 位中国大陆手机号</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-25
 */
public class UserUpdateDTO {

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
