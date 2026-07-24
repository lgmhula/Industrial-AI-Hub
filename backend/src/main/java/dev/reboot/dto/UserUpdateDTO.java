package dev.reboot.dto;

/**
 * 用户编辑请求 DTO —— 管理员编辑用户信息时使用。
 *
 * @author hula0710
 * @since 2026-07-25
 */
public class UserUpdateDTO {

    private String email;
    private String phone;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
