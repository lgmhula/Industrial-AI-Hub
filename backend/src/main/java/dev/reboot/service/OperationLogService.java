package dev.reboot.service;

import dev.reboot.entity.OperationLog;
import dev.reboot.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OperationLog 业务逻辑层。
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
}
