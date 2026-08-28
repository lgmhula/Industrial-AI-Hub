package dev.reboot.service;

import dev.reboot.entity.OperationLog;
import dev.reboot.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OperationLogService 单元测试。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock private OperationLogMapper operationLogMapper;
    @InjectMocks private OperationLogService operationLogService;

    @Test
    void listRecent_shouldReturnLogs() {
        OperationLog ol = new OperationLog(); ol.setId(1L);
        when(operationLogMapper.findRecent()).thenReturn(List.of(ol));
        List<OperationLog> result = operationLogService.listRecent();
        assertEquals(1, result.size());
    }

    @Test
    void listRecent_shouldReturnEmpty() {
        when(operationLogMapper.findRecent()).thenReturn(Collections.emptyList());
        assertTrue(operationLogService.listRecent().isEmpty());
    }

    @Test
    void listPaged_shouldReturnPage() {
        OperationLog ol = new OperationLog(); ol.setId(1L);
        when(operationLogMapper.findAll(null, null)).thenReturn(List.of(ol));
        var result = operationLogService.listPaged(1, 20, null, null);
        assertEquals(1, result.getTotal());
    }

    @Test
    void listPaged_shouldReturnEmptyPage() {
        when(operationLogMapper.findAll(null, null)).thenReturn(Collections.emptyList());
        var result = operationLogService.listPaged(1, 20, null, null);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void listByUserId_shouldReturnUserLogs() {
        OperationLog ol = new OperationLog(); ol.setId(1L); ol.setUserId(5L);
        when(operationLogMapper.findByUserId(5L)).thenReturn(List.of(ol));
        var result = operationLogService.listByUserId(5L, 1, 20);
        assertEquals(1, result.getTotal());
        assertEquals(5L, result.getList().get(0).getUserId());
    }

    @Test
    void listByUserId_shouldReturnEmpty() {
        when(operationLogMapper.findByUserId(99L)).thenReturn(Collections.emptyList());
        var result = operationLogService.listByUserId(99L, 1, 20);
        assertEquals(0, result.getTotal());
    }
}
