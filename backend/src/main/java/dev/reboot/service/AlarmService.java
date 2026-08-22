package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.dto.AlarmVO;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Alarm 业务逻辑层 —— 报警查询 + 确认/解决 + 规则触发（P1-01：站点作用域）。
 *
 * <p>告警归属经 device 继承站点：确认/解决需设备所在站点 OPERATOR 及以上；
 * 列表查询按当前用户可访问站点过滤。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Service
public class AlarmService {

    private final AlarmMapper alarmMapper;
    private final DeviceMapper deviceMapper;
    private final SiteAccessService siteAccessService;

    public AlarmService(AlarmMapper alarmMapper, DeviceMapper deviceMapper,
                        SiteAccessService siteAccessService) {
        this.alarmMapper = alarmMapper;
        this.deviceMapper = deviceMapper;
        this.siteAccessService = siteAccessService;
    }

    /** 分页查询全部告警（当前用户可访问站点）。 */
    public PageInfo<AlarmVO> listAllPaged(int page, int size, Long userId) {
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        if (siteIds != null && siteIds.isEmpty()) {
            return emptyPage(page, size);
        }
        PageHelper.startPage(page, size);
        List<Alarm> records = alarmMapper.findAll(siteIds);
        return toPageInfo(records, page, size);
    }

    /** 按设备 ID 分页查询（需设备所在站点 VIEWER 及以上）。 */
    public PageInfo<AlarmVO> listByDevicePaged(Long deviceId, int page, int size, Long userId) {
        Device device = requireDevice(deviceId);
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.VIEWER);
        PageHelper.startPage(page, size);
        List<Alarm> records = alarmMapper.findByDeviceId(deviceId);
        return toPageInfo(records, page, size);
    }

    /** 按状态分页查询（当前用户可访问站点）。 */
    public PageInfo<AlarmVO> listByStatusPaged(Integer status, int page, int size, Long userId) {
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        if (siteIds != null && siteIds.isEmpty()) {
            return emptyPage(page, size);
        }
        PageHelper.startPage(page, size);
        List<Alarm> records = alarmMapper.findByStatus(status, siteIds);
        return toPageInfo(records, page, size);
    }

    /** 确认告警（需告警所属设备站点 OPERATOR 及以上）。 */
    public boolean acknowledge(Long id, Long userId) {
        assertAlarmSiteAccess(id, userId, RoleEnum.OPERATOR);
        return alarmMapper.acknowledge(id) > 0;
    }

    /** 解决告警（需告警所属设备站点 OPERATOR 及以上）。 */
    public boolean resolve(Long id, Long userId) {
        assertAlarmSiteAccess(id, userId, RoleEnum.OPERATOR);
        return alarmMapper.resolve(id) > 0;
    }

    /** 创建告警记录（系统内部：规则引擎/MQ 触发，非用户请求）。 */
    public AlarmVO createAlarm(Long deviceId, String alarmType, Integer alarmLevel,
                               String alarmMessage, LocalDateTime triggeredAt) {
        Alarm alarm = new Alarm();
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setAlarmMessage(alarmMessage);
        alarm.setStatus(0);
        alarm.setTriggeredAt(triggeredAt);
        alarmMapper.insert(alarm);
        return AlarmVO.from(alarm);
    }

    /** 告警不存在 → 返回 false（保持原语义，不抛 404）；否则校验站点访问。 */
    private void assertAlarmSiteAccess(Long alarmId, Long userId, RoleEnum required) {
        Alarm alarm = alarmMapper.findById(alarmId);
        if (alarm == null) {
            return;
        }
        Device device = deviceMapper.findById(alarm.getDeviceId());
        if (device == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该告警");
        }
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), required);
    }

    private Device requireDevice(Long deviceId) {
        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return device;
    }

    private PageInfo<AlarmVO> toPageInfo(List<Alarm> records, int page, int size) {
        PageInfo<Alarm> raw = new PageInfo<>(records);
        List<AlarmVO> voList = records.stream().map(AlarmVO::from).toList();
        PageInfo<AlarmVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(raw.getTotal());
        result.setPageNum(raw.getPageNum());
        result.setPageSize(raw.getPageSize());
        result.setPages(raw.getPages());
        result.setSize(voList.size());
        return result;
    }

    private PageInfo<AlarmVO> emptyPage(int page, int size) {
        PageInfo<AlarmVO> result = new PageInfo<>();
        result.setList(List.of());
        result.setTotal(0);
        result.setPageNum(page);
        result.setPageSize(size);
        result.setPages(0);
        result.setSize(0);
        return result;
    }
}
