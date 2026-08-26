package dev.reboot.mapper;

import dev.reboot.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * User 表 Mapper。
 *
 * @author hula0710
 * @since 2026-07-25
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE is_deleted = 0 ORDER BY id DESC")
    List<User> findAll();

    @Select("SELECT * FROM user WHERE is_deleted = 0 AND (username LIKE CONCAT('%',#{keyword},'%') OR email LIKE CONCAT('%',#{keyword},'%')) ORDER BY id DESC")
    List<User> search(@Param("keyword") String keyword);

    @Select("SELECT * FROM user WHERE id = #{id} AND is_deleted = 0")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username} AND is_deleted = 0")
    User findByUsername(String username);

    @Insert("INSERT INTO user(username, password, email, phone, status) "
          + "VALUES(#{username}, #{password}, #{email}, #{phone}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE user SET email=#{email}, phone=#{phone} WHERE id=#{id} AND is_deleted = 0")
    int update(User user);

    /** 启用/禁用用户。 */
    @Update("UPDATE user SET status=#{status} WHERE id=#{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE user SET password=#{password} WHERE id=#{id} AND is_deleted = 0")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /** 逻辑删除（非物理删除），与 Device 保持一致。 */
    @Update("UPDATE user SET is_deleted = 1 WHERE id = #{id}")
    int softDeleteById(Long id);

    /* ===== P1-02-A-2 用户安全状态 ===== */

    /** 更新连续登录失败次数。 */
    @Update("UPDATE user SET failed_attempts = #{failedAttempts} WHERE id = #{id} AND is_deleted = 0")
    int updateFailedAttempts(@Param("id") Long id, @Param("failedAttempts") Integer failedAttempts);

    /** 更新持久锁定截止时间（NULL=解锁）。 */
    @Update("UPDATE user SET locked_until = #{lockedUntil} WHERE id = #{id} AND is_deleted = 0")
    int updateLockedUntil(@Param("id") Long id, @Param("lockedUntil") java.time.LocalDateTime lockedUntil);

    /** 成功登录/管理员解锁：清零失败次数并清除锁定。 */
    @Update("UPDATE user SET failed_attempts = 0, locked_until = NULL WHERE id = #{id} AND is_deleted = 0")
    int resetLoginSecurity(@Param("id") Long id);

    /** P1-02-A-4：记录最近改密时间（旧 token 失效基准）。 */
    @Update("UPDATE user SET password_changed_at = #{passwordChangedAt} WHERE id = #{id} AND is_deleted = 0")
    int updatePasswordChangedAt(@Param("id") Long id,
                                @Param("passwordChangedAt") java.time.LocalDateTime passwordChangedAt);
}
