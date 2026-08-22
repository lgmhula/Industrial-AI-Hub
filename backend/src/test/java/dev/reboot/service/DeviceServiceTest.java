package dev.reboot.service;

import com.github.pagehelper.PageInfo;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
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
 * DeviceService 单元测试（P1-01：站点作用域）。
 *
 * <p>Mock DeviceMapper + SiteAccessService（授权核心逻辑在 SiteAccessServiceTest 单独覆盖；
 * 本测试验证 Service 调用了站点断言并传播 403）。</p>
 *
 * @author hula0710
 * @since 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private SiteAccessService siteAccessService;

    @InjectMocks
    private DeviceService deviceService;

    private static final Long USER_ID = 1L;

    /* ---- helpers ---- */

    private Device newDevice(Long id, String name, String code, String type, Integer status) {
        Device d = new Device();
        d.setId(id);
        d.setSiteId(10L);
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
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(deviceMapper.findAll(null)).thenReturn(Arrays.asList(d1, d2));

        List<DeviceVO> result = deviceService.listAll(USER_ID);

        assertEquals(2, result.size());
        assertEquals("泵A", result.get(0).getDeviceName());
    }

    @Test
    void listAll_shouldReturnEmptyList() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(deviceMapper.findAll(null)).thenReturn(Collections.emptyList());
        assertTrue(deviceService.listAll(USER_ID).isEmpty());
    }

    @Test
    void listAll_noSiteAccess_shouldReturnEmpty() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(List.of());
        assertTrue(deviceService.listAll(USER_ID).isEmpty());
        verify(deviceMapper, never()).findAll(any());
    }

    /* ==============================
     * getById
     * ============================== */

    @Test
    void getById_shouldReturnDeviceVO() {
        Device d = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(d);

        DeviceVO vo = deviceService.getById(1L, USER_ID);

        assertEquals("PUMP-001", vo.getDeviceCode());
        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.VIEWER);
    }

    @Test
    void getById_shouldThrowNotFound() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.getById(99L, USER_ID));
        assertEquals(404, ex.getErrorCode().getCode());
    }

    @Test
    void getById_noSiteAccess_shouldThrowForbidden() {
        Device d = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(d);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.VIEWER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.getById(1L, USER_ID));
        assertEquals(403, ex.getErrorCode().getCode());
    }

    /* ==============================
     * create
     * ============================== */

    @Test
    void create_shouldInsertAndReturnVO() {
        DeviceDTO dto = newDeviceDTO("传感器", "SENSOR-001", "传感器", 1);
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(List.of(10L));
        when(deviceMapper.findByCode("SENSOR-001")).thenReturn(null);

        DeviceVO vo = deviceService.create(dto, USER_ID);

        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);
        verify(deviceMapper).insert(any(Device.class));
        assertEquals("SENSOR-001", vo.getDeviceCode());
        assertEquals(1, vo.getStatus());
    }

    @Test
    void create_nullStatus_shouldDefaultTo1() {
        DeviceDTO dto = newDeviceDTO("传感器", "SENSOR-002", "传感器", null);
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(List.of(10L));
        when(deviceMapper.findByCode("SENSOR-002")).thenReturn(null);

        DeviceVO vo = deviceService.create(dto, USER_ID);

        assertEquals(1, vo.getStatus());
    }

    @Test
    void create_duplicateCode_shouldThrowConflict() {
        DeviceDTO dto = newDeviceDTO("传感器", "DUP-001", "传感器", 1);
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(List.of(10L));
        when(deviceMapper.findByCode("DUP-001")).thenReturn(new Device());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.create(dto, USER_ID));
        assertEquals(409, ex.getErrorCode().getCode());
        verify(deviceMapper, never()).insert(any());
    }

    @Test
    void create_multiSiteWithoutSiteId_shouldReject() {
        DeviceDTO dto = newDeviceDTO("传感器", "SENSOR-003", "传感器", 1);
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(List.of(10L, 20L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.create(dto, USER_ID));
        assertEquals(400, ex.getErrorCode().getCode());
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
        DeviceVO vo = deviceService.update(1L, dto, USER_ID);

        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);
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
        DeviceVO vo = deviceService.update(1L, dto, USER_ID);

        assertEquals(1, vo.getStatus());
    }

    @Test
    void update_shouldThrowNotFound() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        DeviceDTO dto = newDeviceDTO("X", null, "X", 1);
        assertThrows(BusinessException.class, () -> deviceService.update(99L, dto, USER_ID));
    }

    @Test
    void update_noSiteAccess_shouldThrowForbidden() {
        Device existing = newDevice(1L, "泵A", "DEV-001", "泵", 1);
        when(deviceMapper.findById(1L)).thenReturn(existing);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);

        DeviceDTO dto = newDeviceDTO("泵A", null, "泵", 1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> deviceService.update(1L, dto, USER_ID));
        assertEquals(403, ex.getErrorCode().getCode());
        verify(deviceMapper, never()).update(any());
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
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(deviceMapper.findByType("泵", null)).thenReturn(List.of(d));

        List<DeviceVO> result = deviceService.listByType("泵", USER_ID);

        assertEquals(1, result.size());
        assertEquals("泵", result.get(0).getDeviceType());
    }

    @Test
    void listByType_shouldReturnEmpty() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(deviceMapper.findByType("未知", null)).thenReturn(Collections.emptyList());
        assertTrue(deviceService.listByType("未知", USER_ID).isEmpty());
    }

    /* ==============================
     * searchDevices
     * ============================== */

    @Test
    void searchDevices_shouldReturnPage() {
        Device d1 = newDevice(1L, "泵A", "PUMP-001", "泵", 1);
        Device d2 = newDevice(2L, "泵B", "PUMP-002", "泵", 1);
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(deviceMapper.searchDevices("泵", null, null, null))
                .thenReturn(Arrays.asList(d1, d2));

        PageInfo<DeviceVO> result = deviceService.searchDevices("泵", null, null, 1, 10, USER_ID);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("PUMP-001", result.getList().get(0).getDeviceCode());
    }

    @Test
    void searchDevices_shouldReturnEmptyPage() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(deviceMapper.searchDevices("XYZ", null, null, null))
                .thenReturn(Collections.emptyList());

        PageInfo<DeviceVO> result = deviceService.searchDevices("XYZ", null, null, 1, 10, USER_ID);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }
}
