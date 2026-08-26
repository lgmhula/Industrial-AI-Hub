package dev.reboot.mapper;

import dev.reboot.entity.LoginAudit;
import org.apache.ibatis.annotations.*;

/**
 * login_audit 表 Mapper（P1-02-A-5）—— 登录审计落库。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Mapper
public interface LoginAuditMapper {

    @Insert("INSERT INTO login_audit(user_id, username, success, ip_address, user_agent, reason) "
          + "VALUES(#{userId}, #{username}, #{success}, #{ipAddress}, #{userAgent}, #{reason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LoginAudit audit);
}
