package dev.reboot.service;

import dev.reboot.entity.LoginAudit;
import dev.reboot.mapper.LoginAuditMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LoginAuditService 单元测试（P1-02-A-5）。
 *
 * <p>覆盖：异步记录字段正确、写入失败不影响调用方（catch）、
 * 安全边界（实体与 migration 不含 password/token/secret）。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class LoginAuditServiceTest {

    @Mock private LoginAuditMapper loginAuditMapper;
    @InjectMocks private LoginAuditService loginAuditService;

    @Test
    void record_shouldInsertCorrectFields() {
        loginAuditService.record(1L, "admin", true, "1.2.3.4", "Mozilla/5.0", "SUCCESS");

        verify(loginAuditMapper).insert(argThat(a ->
                a.getUserId().equals(1L)
                        && "admin".equals(a.getUsername())
                        && a.getSuccess() == 1
                        && "1.2.3.4".equals(a.getIpAddress())
                        && "Mozilla/5.0".equals(a.getUserAgent())
                        && "SUCCESS".equals(a.getReason())));
    }

    @Test
    void record_failure_shouldHaveSuccessZeroAndNullUser() {
        loginAuditService.record(null, "ghost", false, "1.2.3.4", null, "INVALID_CREDENTIAL");
        verify(loginAuditMapper).insert(argThat(a ->
                a.getUserId() == null && a.getSuccess() == 0 && "ghost".equals(a.getUsername())));
    }

    @Test
    void record_mapperError_shouldNotPropagate() {
        when(loginAuditMapper.insert(any(LoginAudit.class)))
                .thenThrow(new RuntimeException("db down"));
        // 审计失败不影响登录结果（异常被吞，仅 log.error）
        assertDoesNotThrow(() ->
                loginAuditService.record(1L, "admin", true, "ip", "ua", "SUCCESS"));
    }

    @Test
    void entity_shouldNotContainSensitiveFields() {
        for (Field field : LoginAudit.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("password") || name.contains("token") || name.contains("secret"),
                    "LoginAudit 不得包含敏感字段: " + field.getName());
        }
    }

    @Test
    void migration_shouldNotContainSensitiveColumns() throws Exception {
        String sql = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V6__add_login_audit.sql").getInputStream(),
                StandardCharsets.UTF_8).toLowerCase();
        // 匹配反引号列定义（注释中的 "password" 字样属说明，不算列）
        assertFalse(sql.contains("`password`"), "V6 migration 不得含 password 列");
        assertFalse(sql.contains("`token`"), "V6 migration 不得含 token 列");
        assertFalse(sql.contains("`secret`"), "V6 migration 不得含 secret 列");
    }
}
