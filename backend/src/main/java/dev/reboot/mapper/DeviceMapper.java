package dev.reboot.mapper;

import dev.reboot.entity.Device;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Device 表 Mapper —— 演示 MyBatis 注解 + Spring Boot 自动注入。
 *
 * <p>@Mapper 让 MyBatis-Spring 自动生成代理 Bean，无需手动 SqlSession。</p>
 *
 * @author hula0710
 * @since 2026-07-19
 */
@Mapper
public interface DeviceMapper {

    @Select("SELECT id, name, type, status FROM device")
    List<Device> findAll();

    @Select("SELECT id, name, type, status FROM device WHERE id = #{id}")
    Device findById(Long id);

    @Insert("INSERT INTO device(name, type, status) VALUES(#{name}, #{type}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Device device);

    @Update("UPDATE device SET name=#{name}, type=#{type}, status=#{status} WHERE id=#{id}")
    int update(Device device);

    @Delete("DELETE FROM device WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT id, name, type, status FROM device WHERE type = #{type}")
    List<Device> findByType(String type);
}
