package dev.reboot.service;

import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Device 业务逻辑层。
 *
 * <p>错误均通过 {@link BusinessException} 抛出。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    /** 查询所有设备，返回 DeviceVO（不含内部标记字段）。 */
    public List<DeviceVO> listAll() {
        return deviceMapper.findAll().stream()
                .map(DeviceVO::from)
                .toList();
    }

    /**
     * 按 ID 查询，返回 DeviceVO。
     *
     * @throws BusinessException 设备不存在 → 404
     */
    public DeviceVO getById(Long id) {
        Device device = deviceMapper.findById(id);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return DeviceVO.from(device);
    }

    /**
     * 创建设备。
     *
     * @throws BusinessException deviceCode 重复 → 409
     */
    public DeviceVO create(DeviceDTO dto) {
        if (deviceMapper.findByCode(dto.getDeviceCode()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "设备编码已存在: " + dto.getDeviceCode());
        }
        Device device = new Device();
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceType(dto.getDeviceType());
        device.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        device.setIpAddress(dto.getIpAddress());
        device.setPort(dto.getPort());
        device.setLocation(dto.getLocation());
        deviceMapper.insert(device);
        return DeviceVO.from(device);
    }

    /**
     * 更新设备。
     *
     * @throws BusinessException 设备不存在 → 404
     */
    public DeviceVO update(Long id, DeviceDTO dto) {
        Device device = deviceMapper.findById(id);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        if (dto.getStatus() != null) {
            device.setStatus(dto.getStatus());
        }
        device.setIpAddress(dto.getIpAddress());
        device.setPort(dto.getPort());
        device.setLocation(dto.getLocation());
        deviceMapper.update(device);
        return DeviceVO.from(device);
    }

    /**
     * 逻辑删除设备。
     *
     * @throws BusinessException 设备不存在 → 404
     */
    public boolean delete(Long id) {
        if (deviceMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return deviceMapper.softDeleteById(id) > 0;
    }

    /** 按设备类型查询（接入之前未使用的 findByType()）。 */
    public java.util.List<dev.reboot.dto.DeviceVO> listByType(String deviceType) {
        return deviceMapper.findByType(deviceType).stream()
                .map(dev.reboot.dto.DeviceVO::from)
                .toList();
    }

    /**
     * 分页搜索设备 —— 支持关键字模糊、类型筛选、状态筛选。
     *
     * @param keyword   设备名称/编码关键字（null=不限）
     * @param deviceType 设备类型（null=不限）
     * @param status    状态（null=不限）
     * @param page      页码
     * @param size      每页条数
     * @return PageInfo 分页结果
     */
    public com.github.pagehelper.PageInfo<dev.reboot.dto.DeviceVO> searchDevices(
            String keyword, String deviceType, Integer status, int page, int size) {
        com.github.pagehelper.PageHelper.startPage(page, size);
        java.util.List<dev.reboot.entity.Device> devices = deviceMapper.searchDevices(keyword, deviceType, status);
        com.github.pagehelper.PageInfo<dev.reboot.entity.Device> raw = new com.github.pagehelper.PageInfo<>(devices);
        java.util.List<dev.reboot.dto.DeviceVO> voList = devices.stream()
                .map(dev.reboot.dto.DeviceVO::from)
                .toList();
        com.github.pagehelper.PageInfo<dev.reboot.dto.DeviceVO> result = new com.github.pagehelper.PageInfo<>();
        result.setList(voList);
        result.setTotal(raw.getTotal());
        result.setPageNum(raw.getPageNum());
        result.setPageSize(raw.getPageSize());
        result.setPages(raw.getPages());
        result.setSize(voList.size());
        return result;
    }
}