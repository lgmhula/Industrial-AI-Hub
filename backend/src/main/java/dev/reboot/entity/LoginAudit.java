package dev.reboot.entity;

import java.time.LocalDateTime;

/**
 * 登录审计实体 —— 对应 reboot.login_audit 表（P1-02-A-5）。
 *
 * <p>仅服务端审计用，响应不回传；禁止写入 password/token/secret。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
public class LoginAudit {

    private Long id;
    private Long userId;
    private String username;
    private Integer success;
    private String ipAddress;
    private String userAgent;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getSuccess() { return success; }
    public void setSuccess(Integer success) { this.success = success; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
