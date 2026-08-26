package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.config.CacheConfig;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.mq.AlarmMessage;
import dev.reboot.mq.AlarmProducer;
import dev.reboot.mq.DeviceDataMessage;
import dev.reboot.mq.DeviceDataProducer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DeviceData 业务逻辑层（P1-01：站点作用域）。
 *
 * <p>读取需设备所在站点 VIEWER 及以上；上报需 OPERATOR 及以上。
 * 缓存 key 含 userId（避免缓存命中绕过站点授权）。</p>
 *
 * @author hula0710
 * @since 2026-07-26
 */
@Service
public class DeviceDataService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataService.class);

    private final DeviceDataMapper deviceDataMapper;
    private final AlarmDetector alarmDetector;
    private final DeviceMapper deviceMapper;
    private final SiteAccessService siteAccessService;

    private final AlarmProducer alarmProducer;
    private final DeviceDataProducer deviceDataProducer;

    public DeviceDataService(DeviceDataMapper deviceDataMapper, AlarmDetector alarmDetector,
                             DeviceMapper deviceMapper, SiteAccessService siteAccessService,
                             @Nullable AlarmProducer alarmProducer,
                             @Nullable DeviceDataProducer deviceDataProducer) {
        this.deviceDataMapper = deviceDataMapper;
        this.alarmDetector = alarmDetector;
        this.deviceMapper = deviceMapper;
        this.siteAccessService = siteAccessService;
        this.alarmProducer = alarmProducer;
        this.deviceDataProducer = deviceDataProducer;
    }

    /** 按设备 ID 查询所有数据记录（需设备站点 VIEWER 及以上）。 */
    public List<DeviceData> listByDevice(Long deviceId, Long userId) {
        requireDeviceAccess(deviceId, userId, RoleEnum.VIEWER);
        return deviceDataMapper.findByDeviceId(deviceId);
    }

    /** 获取设备最新一条数据（需设备站点 VIEWER 及以上）。 */
    public DeviceData getLatest(Long deviceId, String dataType, Long userId) {
        requireDeviceAccess(deviceId, userId, RoleEnum.VIEWER);
        return deviceDataMapper.findLatest(deviceId, dataType);
    }

    /**
     * 上报设备数据，同时执行报警规则检测（需设备站点 OPERATOR 及以上）。
     */
    @CacheEvict(cacheNames = {CacheConfig.CACHE_DEVICE_STATS, CacheConfig.CACHE_DEVICE_RANGE},
            allEntries = true)
    public DeviceData report(Long deviceId, DataReportRequest req, Long userId) {
        requireDeviceAccess(deviceId, userId, RoleEnum.OPERATOR);

        DeviceData data = new DeviceData();
        data.setDeviceId(deviceId);
        data.setDataType(req.getDataType());
        data.setDataValue(req.getDataValue());
        data.setUnit(req.getUnit());
        data.setRecordedAt(LocalDateTime.now());
        deviceDataMapper.insert(data);

        // ── 发布/订阅：广播设备数据到下游（日志归档 + 实时分析） ──
        if (deviceDataProducer != null) {
            DeviceDataMessage dataMsg = new DeviceDataMessage(
                    deviceId, req.getDataType(), req.getDataValue(),
                    req.getUnit(), data.getRecordedAt());
            deviceDataProducer.publish(dataMsg);
        }

        // ── 报警检测 ──
        List<AlarmVO> alarms = alarmDetector.check(deviceId, req.getDataType(), req.getDataValue());
        if (!alarms.isEmpty()) {
            log.warn("报警触发 device={} alarms={}", deviceId, alarms.size());

            // ── 异步发送报警消息到 RabbitMQ（工作队列） ──
            if (alarmProducer != null) {
                for (AlarmVO alarm : alarms) {
                    AlarmMessage msg = new AlarmMessage(
                            deviceId,
                            alarm.getAlarmType(),
                            alarm.getAlarmLevel(),
                            alarm.getAlarmMessage(),
                            req.getDataValue(),
                            data.getRecordedAt());
                    alarmProducer.send(msg);

                    // ── Day 54: 同时发送延迟检查（30s 后未处理则升级） ──
                    alarmProducer.sendDelayCheck(msg);
                }
            }
        }

        return data;
    }

    /**
     * 按时间范围查询设备数据（需设备站点 VIEWER 及以上；缓存 key 含 userId）。
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_DEVICE_RANGE,
            key = "#userId + ':' + #deviceId + ':' + #dataType + ':' + #startTime + ':' + #endTime")
    public List<DeviceData> listByTimeRange(Long deviceId, String dataType,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            Long userId) {
        requireDeviceAccess(deviceId, userId, RoleEnum.VIEWER);
        return deviceDataMapper.findByTimeRange(deviceId, dataType, startTime, endTime);
    }

    /**
     * 聚合统计：avg/min/max/count（需设备站点 VIEWER 及以上；缓存 key 含 userId）。
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_DEVICE_STATS,
            key = "#userId + ':' + #deviceId + ':' + #dataType + ':' + #startTime + ':' + #endTime")
    public DeviceDataStats getStats(Long deviceId, String dataType,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    Long userId) {
        requireDeviceAccess(deviceId, userId, RoleEnum.VIEWER);
        Map<String, Object> raw = deviceDataMapper.aggregate(deviceId, dataType, startTime, endTime);
        DeviceDataStats stats = new DeviceDataStats();
        stats.setAvg((BigDecimal) raw.get("avg"));
        stats.setMin((BigDecimal) raw.get("min"));
        stats.setMax((BigDecimal) raw.get("max"));
        stats.setCount(((Number) raw.get("cnt")).longValue());
        return stats;
    }

    private void requireDeviceAccess(Long deviceId, Long userId, RoleEnum required) {
        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), required);
    }
}
