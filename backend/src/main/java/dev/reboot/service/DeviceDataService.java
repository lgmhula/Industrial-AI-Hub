package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.DeviceDataMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DeviceData 业务逻辑层。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@Service
public class DeviceDataService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataService.class);

    private final DeviceDataMapper deviceDataMapper;

    private final AlarmDetector alarmDetector;

    public DeviceDataService(DeviceDataMapper deviceDataMapper, AlarmDetector alarmDetector) {
        this.deviceDataMapper = deviceDataMapper;
        this.alarmDetector = alarmDetector;
    }

    /** 按设备 ID 查询所有数据记录。 */
    public List<DeviceData> listByDevice(Long deviceId) {
        return deviceDataMapper.findByDeviceId(deviceId);
    }

    /** 获取设备最新一条数据。 */
    public DeviceData getLatest(Long deviceId, String dataType) {
        return deviceDataMapper.findLatest(deviceId, dataType);
    }

    /**
     * 上报设备数据。
     *
     * @return 持久化后的 DeviceData
     */
    /**
     * 上报设备数据，同时执行报警规则检测。
     *
     * @return 持久化后的 DeviceData
     */
    public DeviceData report(Long deviceId, DataReportRequest req) {
        DeviceData data = new DeviceData();
        data.setDeviceId(deviceId);
        data.setDataType(req.getDataType());
        data.setDataValue(req.getDataValue());
        data.setUnit(req.getUnit());
        data.setRecordedAt(LocalDateTime.now());
        deviceDataMapper.insert(data);

        // ── 报警检测 ──
        List<AlarmVO> alarms = alarmDetector.check(deviceId, req.getDataType(), req.getDataValue());
        if (!alarms.isEmpty()) {
            // 报警已由 detector 内部持久化，此处仅作日志
            log.warn("报警触发 device={} alarms={}", deviceId, alarms.size());
        }

        return data;
    }

    /**
     * 按时间范围查询设备数据。
     *
     * @param deviceId  设备 ID
     * @param dataType  数据类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     */
    public List<DeviceData> listByTimeRange(Long deviceId, String dataType,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        return deviceDataMapper.findByTimeRange(deviceId, dataType, startTime, endTime);
    }

    /**
     * 聚合统计：avg/min/max/count。
     */
    public DeviceDataStats getStats(Long deviceId, String dataType,
                                    LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> raw = deviceDataMapper.aggregate(deviceId, dataType, startTime, endTime);
        DeviceDataStats stats = new DeviceDataStats();
        stats.setAvg((BigDecimal) raw.get("avg"));
        stats.setMin((BigDecimal) raw.get("min"));
        stats.setMax((BigDecimal) raw.get("max"));
        stats.setCount(((Number) raw.get("cnt")).longValue());
        return stats;
    }
}
