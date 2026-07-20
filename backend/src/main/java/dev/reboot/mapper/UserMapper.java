package dev.reboot.mapper;

import dev.reboot.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * User 表 Mapper。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user ORDER BY id DESC")
    List<User> findAll();

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("INSERT INTO user(username, password, email, phone, status) "
          + "VALUES(#{username}, #{password}, #{email}, #{phone}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE user SET email=#{email}, phone=#{phone}, status=#{status} WHERE id=#{id}")
    int update(User user);

    @Update("UPDATE user SET password=#{password} WHERE id=#{id}")
    int updatePassword(Long id, String password);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteById(Long id);
}
