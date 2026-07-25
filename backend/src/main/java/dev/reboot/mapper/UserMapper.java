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
    int updatePassword(Long id, String password);

    /** 逻辑删除（非物理删除），与 Device 保持一致。 */
    @Update("UPDATE user SET is_deleted = 1 WHERE id = #{id}")
    int softDeleteById(Long id);
}
