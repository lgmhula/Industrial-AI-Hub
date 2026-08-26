package dev.reboot.entity;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应 reboot.user 表。
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class User {

    private Long id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private Integer status;
    private Integer isDeleted;
    /** P1-02-A-2：连续登录失败次数（成功/解锁归零）。 */
    private Integer failedAttempts;
    /** P1-02-A-2：锁定截止时间（NULL=未锁定）。 */
    private LocalDateTime lockedUntil;
    /** P1-02-A-2：最近改密时间（A-4 旧 token 失效基准）。 */
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public LocalDateTime getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** P1-02-A-2：是否处于持久锁定中（locked_until 非空且未过期）。 */
    public boolean isLockedNow() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    @Override
    public String toString() {
        return String.format("User{id=%d, username='%s', email='%s', status=%d}",
                id, username, email, status);
    }
}
