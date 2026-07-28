package dev.reboot.mapper;

import dev.reboot.entity.OperationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * OperationLog 表 Mapper —— 插入 + 分页查询。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface OperationLogMapper {

    /** 查询最近 100 条（不分页，兼容旧调用）。 */
    @Select("SELECT * FROM operation_log ORDER BY created_at DESC LIMIT 100")
    List<OperationLog> findRecent();

    /** 分页查询。 */
    @Select("SELECT * FROM operation_log ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<OperationLog> findPaged(@Param("offset") int offset, @Param("size") int size);

    /** 按用户 ID 分页查询。 */
    @Select("SELECT * FROM operation_log WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<OperationLog> findByUserIdPaged(@Param("userId") Long userId,
                                         @Param("offset") int offset, @Param("size") int size);

    /** 按操作类型分页查询。 */
    @Select("SELECT * FROM operation_log WHERE operation_type = #{opType} ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<OperationLog> findByOpTypePaged(@Param("opType") String opType,
                                         @Param("offset") int offset, @Param("size") int size);

    /** 统计总数。 */
    @Select("SELECT COUNT(*) FROM operation_log")
    long count();

    /** 按用户 ID 统计。 */
    @Select("SELECT COUNT(*) FROM operation_log WHERE user_id = #{userId}")
    long countByUserId(Long userId);

    /** 按操作类型统计。 */
    @Select("SELECT COUNT(*) FROM operation_log WHERE operation_type = #{opType}")
    long countByOpType(String opType);

    /** 插入操作日志。 */
    @Insert("INSERT INTO operation_log(user_id, operation_type, target_type, target_id, description, ip_address) "
          + "VALUES(#{userId}, #{operationType}, #{targetType}, #{targetId}, #{description}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);
}
