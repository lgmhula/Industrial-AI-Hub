package dev.reboot.service;

import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Device 业务逻辑层（P1-01：站点资源作用域）。
 *
 * <p>所有设备访问按「设备所属站点」做授权：全局 ADMIN 放行，
 * 普通用户需 user_site 成员且站点内角色满足要求（{@link SiteAccessService}）。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceMapper deviceMapper;
    private final SiteAccessService siteAccessService;

    public DeviceService(DeviceMapper deviceMapper, SiteAccessService siteAccessService) {
        this.deviceMapper = deviceMapper;
        this.siteAccessService = siteAccessService;
    }

    /** 查询所有设备（当前用户可访问站点；管理员=全部）。 */
    public List<DeviceVO> listAll(Long userId) {
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        if (siteIds != null && siteIds.isEmpty()) {
            return List.of();
        }
        return deviceMapper.findAll(siteIds).stream()
                .map(DeviceVO::from)
                .toList();
    }

    /**
     * 按 ID 查询（Spring Cache；key 含 userId，避免缓存命中绕过站点授权）。
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_DEVICE_DETAIL, key = "#userId + ':' + #id")
    public DeviceVO getById(Long id, Long userId) {
        Device device = deviceMapper.findById(id);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.VIEWER);
        return DeviceVO.from(device);
    }

    /**
     * 创建设备（P1-01：设备必须归属站点；创建者需该站点 OPERATOR 及以上）。
     *
     * @throws BusinessException deviceCode 重复 → 409
     */
    public DeviceVO create(DeviceDTO dto, Long userId) {
        Long siteId = resolveCreateSiteId(dto.getSiteId(), userId);
        siteAccessService.assertSiteAccess(userId, siteId, RoleEnum.OPERATOR);

        if (deviceMapper.findByCode(dto.getDeviceCode()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "设备编码已存在: " + dto.getDeviceCode());
        }
        Device device = new Device();
        device.setSiteId(siteId);
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
     * 更新设备（站点归属不可经 update 变更；需该站点 OPERATOR 及以上）。
     *
     * @throws BusinessException 设备不存在 → 404
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_DEVICE_DETAIL, allEntries = true)
    public DeviceVO update(Long id, DeviceDTO dto, Long userId) {
        Device device = deviceMapper.findById(id);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.OPERATOR);
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
     * 逻辑删除设备（全局 ADMIN 专属，@RequireRole(ADMIN) 已拦截）。
     *
     * @throws BusinessException 设备不存在 → 404
     */
    @CacheEvict(cacheNames = CacheConfig.CACHE_DEVICE_DETAIL, allEntries = true)
    public boolean delete(Long id) {
        if (deviceMapper.findById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return deviceMapper.softDeleteById(id) > 0;
    }

    /** 按设备类型查询（当前用户可访问站点）。 */
    public List<DeviceVO> listByType(String deviceType, Long userId) {
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        if (siteIds != null && siteIds.isEmpty()) {
            return List.of();
        }
        return deviceMapper.findByType(deviceType, siteIds).stream()
                .map(DeviceVO::from)
                .toList();
    }

    /**
     * 分页搜索设备 —— 支持关键字模糊、类型筛选、状态筛选 + 站点范围。
     */
    public PageInfo<DeviceVO> searchDevices(
            String keyword, String deviceType, Integer status, int page, int size, Long userId) {
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        if (siteIds != null && siteIds.isEmpty()) {
            return emptyPage(page, size);
        }
        PageHelper.startPage(page, size);
        List<Device> devices = deviceMapper.searchDevices(keyword, deviceType, status, siteIds);
        PageInfo<Device> raw = new PageInfo<>(devices);
        List<DeviceVO> voList = devices.stream()
                .map(DeviceVO::from)
                .toList();
        PageInfo<DeviceVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(raw.getTotal());
        result.setPageNum(raw.getPageNum());
        result.setPageSize(raw.getPageSize());
        result.setPages(raw.getPages());
        result.setSize(voList.size());
        return result;
    }

    /**
     * 解析创建时的站点：显式 siteId 优先；否则取创建者唯一站点；管理员缺省归默认站点。
     */
    private Long resolveCreateSiteId(Long requestedSiteId, Long userId) {
        if (requestedSiteId != null) {
            return requestedSiteId;
        }
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        if (siteIds == null) {
            // 全局管理员：缺省归默认站点
            Long defaultId = siteAccessService.defaultSiteId();
            if (defaultId != null) {
                return defaultId;
            }
        } else if (siteIds.size() == 1) {
            return siteIds.get(0);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请指定设备所属站点");
    }

    private PageInfo<DeviceVO> emptyPage(int page, int size) {
        PageInfo<DeviceVO> result = new PageInfo<>();
        result.setList(List.of());
        result.setTotal(0);
        result.setPageNum(page);
        result.setPageSize(size);
        result.setPages(0);
        result.setSize(0);
        return result;
    }
}
