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
        if (dto.getStatus() != null) device.setStatus(dto.getStatus());
        device.setIpAddress(dto.getIpAddress());
        device.setPort(dto.getPort());
        device.setLocation(dto.getLocation());
        deviceMapper.update(device);
        return DeviceVO.from(device);
    }

    /** 逻辑删除设备。 */
    public boolean delete(Long id) {
        return deviceMapper.softDeleteById(id) > 0;
    }
}
