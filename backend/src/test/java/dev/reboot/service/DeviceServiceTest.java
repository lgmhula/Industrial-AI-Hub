package dev.reboot.service;

import com.github.pagehelper.PageInfo;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.entity.Device;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceService 单元测试。
 *
 * <p>Mock DeviceMapper，覆盖 CRUD + 搜索 + 类型过滤全部路径。</p>
 *
 * @author hula0710
 * @since 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceMapper deviceMapper;


    @InjectMocks
    private DeviceService deviceService;

    /* ---- helpers ---- */

    private Device newDevice(Long id, String name, String code, String type, Integer status) {
        Device d = new Device();
        d.setId(id);
        d.setDeviceName(name);
        d.setDeviceCode(code);
        d.setDeviceType(type);
        d.setStatus(status);
        d.setIpAddress("192.168.1." + id);
        d.setPort(8080);
        d.setLocation("rack-" + id);
        d.setIsDeleted(0);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        return d;
    }

    private DeviceDTO newDeviceDTO(String name, String code, String type, Integer status) {
        DeviceDTO dto = new DeviceDTO();
        dto.setDeviceName(name);
        dto.setDeviceCode(code);
        dto.setDeviceType(type);
        dto.setStatus(status);
        dto.setIpAddress("10.0.0.1");
        dto.setPort(9090);
        dto.setLocation("lab");
        return dto;
    }

    /* ==============================
     * listAll
     * ============================== */

    @Test
    void listAll_shouldReturnDeviceVOs() {
        Device d1 = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        Device d2 = newDevice(2L, "阀B", "VALVE-001", "阀", 1);
        when(deviceMapper.findAll()).thenReturn(Arrays.asList(d1, d2));

        List<DeviceVO> result = deviceService.listAll();

        assertEquals(2, result.size());
        assertEquals("泵A", result.get(0).getDeviceName());
        // isDeleted must NOT leak
        // isDeleted field intentionally absent from DeviceVO
    }

    @Test
    void listAll_shouldReturnEmptyList() {
        when(deviceMapper.findAll()).thenReturn(Collections.emptyList());
        assertTrue(deviceService.listAll().isEmpty());
    }

    /* ==============================
     * getById
     * ============================== */

    @Test
    void getById_shouldReturnDeviceVO() {
        Device d = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(d);

        DeviceVO vo = deviceService.getById(1L);

        assertEquals("PUMP-001", vo.getDeviceCode());
        // isDeleted field intentionally absent from DeviceVO
    }

    @Test
    void getById_shouldThrowNotFound() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.getById(99L));
        assertEquals(404, ex.getErrorCode().getCode());
    }

    /* ==============================
     * create
     * ============================== */

    @Test
    void create_shouldInsertAndReturnVO() {
        DeviceDTO dto = newDeviceDTO("传感器", "SENSOR-001", "传感器", 1);
        when(deviceMapper.findByCode("SENSOR-001")).thenReturn(null);

        DeviceVO vo = deviceService.create(dto);

        verify(deviceMapper).insert(any(Device.class));
        assertEquals("SENSOR-001", vo.getDeviceCode());
        assertEquals(1, vo.getStatus());
    }

    @Test
    void create_nullStatus_shouldDefaultTo1() {
        DeviceDTO dto = newDeviceDTO("传感器", "SENSOR-002", "传感器", null);
        when(deviceMapper.findByCode("SENSOR-002")).thenReturn(null);

        DeviceVO vo = deviceService.create(dto);

        assertEquals(1, vo.getStatus());
    }

    @Test
    void create_duplicateCode_shouldThrowConflict() {
        DeviceDTO dto = newDeviceDTO("传感器", "DUP-001", "传感器", 1);
        when(deviceMapper.findByCode("DUP-001")).thenReturn(new Device());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.create(dto));
        assertEquals(409, ex.getErrorCode().getCode());
        verify(deviceMapper, never()).insert(any());
    }

    /* ==============================
     * update
     * ============================== */

    @Test
    void update_shouldUpdateFields() {
        Device existing = newDevice(1L, "旧名", "DEV-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(existing);

        DeviceDTO dto = newDeviceDTO("新名", null, "阀", 0);
        DeviceVO vo = deviceService.update(1L, dto);

        verify(deviceMapper).update(existing);
        assertEquals("新名", vo.getDeviceName());
        assertEquals("阀", vo.getDeviceType());
        assertEquals(0, vo.getStatus());
    }

    @Test
    void update_nullStatus_shouldKeepExisting() {
        Device existing = newDevice(1L, "泵A", "DEV-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(existing);

        DeviceDTO dto = newDeviceDTO("泵A", null, "泵", null);
        DeviceVO vo = deviceService.update(1L, dto);

        // status unchanged
        assertEquals(1, vo.getStatus());
    }

    @Test
    void update_shouldThrowNotFound() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        DeviceDTO dto = newDeviceDTO("X", null, "X", 1);
        assertThrows(BusinessException.class, () -> deviceService.update(99L, dto));
    }

    /* ==============================
     * delete (soft)
     * ============================== */

    @Test
    void delete_shouldSoftDelete() {
        Device d = newDevice(1L, "泵A", "DEV-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(d);
        when(deviceMapper.softDeleteById(1L)).thenReturn(1);

        assertTrue(deviceService.delete(1L));
        verify(deviceMapper).softDeleteById(1L);
    }

    @Test
    void delete_shouldThrowNotFound() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> deviceService.delete(99L));
        verify(deviceMapper, never()).softDeleteById(anyLong());
    }

    /* ==============================
     * listByType
     * ============================== */

    @Test
    void listByType_shouldReturnFiltered() {
        Device d = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        when(deviceMapper.findByType("泵")).thenReturn(List.of(d));

        List<DeviceVO> result = deviceService.listByType("泵");

        assertEquals(1, result.size());
        assertEquals("泵", result.get(0).getDeviceType());
    }

    @Test
    void listByType_shouldReturnEmpty() {
        when(deviceMapper.findByType("未知")).thenReturn(Collections.emptyList());
        assertTrue(deviceService.listByType("未知").isEmpty());
    }

    /* ==============================
     * searchDevices
     * ============================== */

    @Test
    void searchDevices_shouldReturnPage() {
        Device d1 = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        Device d2 = newDevice(2L, "泵B", "PUMP-002", "泵", 1);
        when(deviceMapper.searchDevices("泵", null, null))
                .thenReturn(Arrays.asList(d1, d2));

        PageInfo<DeviceVO> result = deviceService.searchDevices("泵", null, null, 1, 10);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("PUMP-001", result.getList().get(0).getDeviceCode());
    }

    @Test
    void searchDevices_shouldReturnEmptyPage() {
        when(deviceMapper.searchDevices("XYZ", null, null))
                .thenReturn(Collections.emptyList());

        PageInfo<DeviceVO> result = deviceService.searchDevices("XYZ", null, null, 1, 10);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }
}
