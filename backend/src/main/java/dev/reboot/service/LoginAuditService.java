package dev.reboot.service;

import dev.reboot.entity.LoginAudit;
import dev.reboot.mapper.LoginAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 登录审计服务（P1-02-A-5）—— 异步写入 login_audit。
 *
 * <p>设计：</p>
 * <ul>
 *   <li>{@code @Async("loginAuditExecutor")} 独立线程池异步写入，不阻塞认证主流程；</li>
 *   <li>审计写入失败仅 {@code log.error}，绝不影响登录结果；</li>
 *   <li>禁止写入 password / token / secret（安全边界）。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    public static final String REASON_SUCCESS = "SUCCESS";
    public static final String REASON_INVALID_CREDENTIAL = "INVALID_CREDENTIAL";
    public static final String REASON_INVALID_PASSWORD = "INVALID_PASSWORD";
    public static final String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String REASON_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String REASON_RATE_LIMIT = "RATE_LIMIT";

    private final LoginAuditMapper loginAuditMapper;

    public LoginAuditService(LoginAuditMapper loginAuditMapper) {
        this.loginAuditMapper = loginAuditMapper;
    }

    /**
     * 记录一次登录尝试（异步）。
     *
     * @param userId    用户 ID（登录成功/存在用户时；不存在=NULL）
     * @param username  尝试的用户名（输入值，允许保存——非机密，用于暴力破解归因）
     * @param success   是否成功
     * @param ipAddress 客户端 IP（Controller 传 request.getRemoteAddr()）
     * @param userAgent User-Agent
     * @param reason    结果原因（{@code REASON_*} 常量，仅服务端审计）
     */
    @Async("loginAuditExecutor")
    public void record(Long userId, String username, boolean success,
                       String ipAddress, String userAgent, String reason) {
        try {
            LoginAudit audit = new LoginAudit();
            audit.setUserId(userId);
            audit.setUsername(username);
            audit.setSuccess(success ? 1 : 0);
            audit.setIpAddress(ipAddress);
            audit.setUserAgent(userAgent);
            audit.setReason(reason);
            loginAuditMapper.insert(audit);
        } catch (Exception e) {
            // 审计失败不影响登录结果
            log.error("登录审计写入失败 username={} reason={} err={}", username, reason, e.getMessage());
        }
    }
}
