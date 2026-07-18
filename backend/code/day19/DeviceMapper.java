package code.day19;

import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * DeviceMapper —— MyBatis 纯注解 Mapper。
 *
 * <p>演示用注解替代 XML 映射文件：
 * <ul>
 *   <li>{@code @Select} / {@code @Insert} / {@code @Update} / {@code @Delete}</li>
 *   <li>{@code @Results} 字段映射</li>
 *   <li>动态 SQL via {@code @SelectProvider}</li>
 *   <li>分页 RowBounds 方式</li>
 * </ul>
 *
 * @author Reboot
 * @since 2026-07-18
 */
public interface DeviceMapper {

    @Select("SELECT * FROM device")
    @Results(id = "deviceMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "name", column = "name"),
        @Result(property = "type", column = "type"),
        @Result(property = "location", column = "location"),
        @Result(property = "status", column = "status"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<Device> findAll();

    @Select("SELECT * FROM device WHERE id = #{id}")
    @ResultMap("deviceMap")
    Device findById(@Param("id") Long id);

    @Insert("INSERT INTO device (name, type, location, status) "
            + "VALUES (#{name}, #{type}, #{location}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Device device);

    @Update("UPDATE device SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Delete("DELETE FROM device WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 动态条件查询 */
    @Select("<script>"
            + "SELECT * FROM device WHERE 1=1"
            + "<if test='type != null'> AND type = #{type}</if>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "<if test='keyword != null'> AND (name LIKE CONCAT('%',#{keyword},'%') OR location LIKE CONCAT('%',#{keyword},'%'))</if>"
            + " ORDER BY id"
            + "</script>")
    @ResultMap("deviceMap")
    List<Device> findByCondition(@Param("type") String type,
                                 @Param("status") String status,
                                 @Param("keyword") String keyword);

    /** 批量插入 */
    @Insert("<script>"
            + "INSERT INTO device (name, type, location, status) VALUES "
            + "<foreach collection='devices' item='d' separator=','>"
            + "(#{d.name}, #{d.type}, #{d.location}, #{d.status})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("devices") List<Device> devices);

    @Select("SELECT COUNT(*) FROM device")
    long count();
}
