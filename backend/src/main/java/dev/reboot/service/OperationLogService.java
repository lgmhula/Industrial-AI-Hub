package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.entity.OperationLog;
import dev.reboot.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OperationLog 业务逻辑层 —— 分页查询 + 插入。
 *
 * <p>分页统一使用 PageHelper + PageInfo&lt;OperationLog&gt;。</p>
 *
 * @author hula0710
 * @since 2026-07-26
 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** 查询最近 100 条操作日志。 */
    public List<OperationLog> listRecent() {
        return operationLogMapper.findRecent();
    }

    /** 分页查询全部日志。 */
    public PageInfo<OperationLog> listPaged(int page, int size) {
        PageHelper.startPage(page, size);
        List<OperationLog> records = operationLogMapper.findAll();
        return new PageInfo<>(records);
    }

    /** 按用户 ID 分页查询。 */
    public PageInfo<OperationLog> listByUserId(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<OperationLog> records = operationLogMapper.findByUserId(userId);
        return new PageInfo<>(records);
    }
}
