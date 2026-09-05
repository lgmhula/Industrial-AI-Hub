package dev.reboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.dto.AlarmVO;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.mq.AlarmMessage;
import dev.reboot.mq.AlarmProducer;
import dev.reboot.mq.DeviceDataMessage;
import dev.reboot.mq.DeviceDataProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MQTT 遥测入库服务（Day 96，ADR 0034）。
 *
 * <p>接收模拟 PLC / 真实设备发布到 {@code plc/+/+/telemetry} 的 JSON Payload，
 * 按 Day 95 契约把顶层工程值字段写入 {@code device_data}，并与 REST 上报共用
 * 后续链路（Fanout 广播 + 报警规则检测 + RabbitMQ 报警消息）。</p>
 *
 * <h3>可靠性语义</h3>
 * <ul>
 *   <li>QoS 1 至少一次投递 → Redis SETNX 按
 *       {@code mqtt:{deviceId}:{yyyyMMddHHmmss}:{dataType}} 做 24h 幂等；</li>
 *   <li>Redis 不可用 → 降级为“不幂等仍写入”（宁愿重复不丢数据）；</li>
 *   <li>单字段解析/落库/报警异常 → WARN 跳过该字段，不阻塞同一 Payload 的后续字段；</li>
 *   <li>回调用 try/catch 双层兜底，绝不让异常传播到 Paho 回调线程。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-09-05
 */
@Service
public class MqttDeviceDataIngestService {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceDataIngestService.class);

    private static final String IDEMPOTENCY_KEY_PREFIX = "mqtt:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter IDEMPOTENCY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 模拟器顶层字段 → device_data 的固定映射（Day 95 §2.4 契约）。 */
    private static final Map<String, FieldSpec> FIELD_MAP = fieldMap();

    private final ObjectMapper objectMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceDataMapper deviceDataMapper;
    private final AlarmDetector alarmDetector;

    /** nullable：test profile 下无 Redis Bean 时降级为不做幂等。 */
    @Nullable
    private final StringRedisTemplate redis;

    /** nullable：test profile 下无 RabbitMQ Producer 时跳过广播/报警投递。 */
    @Nullable
    private final DeviceDataProducer deviceDataProducer;

    @Nullable
    private final AlarmProducer alarmProducer;

    public MqttDeviceDataIngestService(ObjectMapper objectMapper,
                                       DeviceMapper deviceMapper,
                                       DeviceDataMapper deviceDataMapper,
                                       AlarmDetector alarmDetector,
                                       @Nullable StringRedisTemplate redis,
                                       @Nullable DeviceDataProducer deviceDataProducer,
                                       @Nullable AlarmProducer alarmProducer) {
        this.objectMapper = objectMapper;
        this.deviceMapper = deviceMapper;
        this.deviceDataMapper = deviceDataMapper;
        this.alarmDetector = alarmDetector;
        this.redis = redis;
        this.deviceDataProducer = deviceDataProducer;
        this.alarmProducer = alarmProducer;
    }

    /**
     * 解析并落库一条 MQTT 遥测。
     *
     * @param topic   MQTT Topic，如 plc/PLANT_A/PLC-M-001/telemetry
     * @param payload UTF-8 JSON Payload
     * @return 是否至少持久化一个字段；非法 JSON / 设备不存在时返回 false
     */
    public boolean ingest(String topic, String payload) {
        if (payload == null || payload.isBlank()) {
            log.warn("MQTT ingest 跳过空 payload: topic={}", topic);
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                log.warn("MQTT ingest 跳过非 JSON Object payload: topic={}", topic);
                return false;
            }
            return ingestObject(topic, root);
        } catch (IOException ex) {
            log.warn("MQTT ingest JSON 解析失败: topic={}", topic, ex);
            return false;
        }
    }

    private boolean ingestObject(String topic, JsonNode root) {
        String deviceCode = text(root.get("deviceCode"));
        if (deviceCode == null) {
            log.warn("MQTT ingest payload 缺少 deviceCode，跳过: topic={}", topic);
            return false;
        }
        checkTopicMismatch(topic, deviceCode);

        Device device = deviceMapper.findByCode(deviceCode);
        if (device == null) {
            log.warn("MQTT ingest 设备不存在或已删除: deviceCode={} topic={}", deviceCode, topic);
            return false;
        }

        LocalDateTime recordedAt = resolveRecordedAt(root.get("ts"));
        int persisted = 0;
        for (FieldSpec spec : FIELD_MAP.values()) {
            try {
                if (persistField(device, root, spec, recordedAt)) {
                    persisted++;
                }
            } catch (RuntimeException ex) {
                // 单字段异常降级：不阻塞同一 Payload 的其他字段
                log.warn("MQTT ingest 单字段处理失败已跳过: deviceId={} field={} topic={}",
                        device.getId(), spec.payloadName, topic, ex);
            }
        }
        log.info("MQTT ingest 完成: deviceCode={} topic={} persisted={}/{}",
                deviceCode, topic, persisted, FIELD_MAP.size());
        return persisted > 0;
    }

    private boolean persistField(Device device, JsonNode root, FieldSpec spec,
                                 LocalDateTime recordedAt) {
        JsonNode valueNode = root.get(spec.payloadName);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return false;
        }
        BigDecimal value = parseNumber(valueNode);
        if (value == null) {
            log.warn("MQTT ingest 字段非数值，跳过: deviceCode={} field={} raw={}",
                    device.getDeviceCode(), spec.payloadName, valueNode.asText());
            return false;
        }

        String idempotencyKey = idempotencyKey(device.getId(), recordedAt, spec.dataType);
        if (!acquireIdempotency(idempotencyKey)) {
            log.info("MQTT ingest 幂等命中跳过: deviceId={} key={}", device.getId(), idempotencyKey);
            return false;
        }

        DeviceData row = new DeviceData();
        row.setDeviceId(device.getId());
        row.setDataType(spec.dataType);
        row.setDataValue(value);
        row.setUnit(spec.unit);
        row.setRecordedAt(recordedAt);
        deviceDataMapper.insert(row);

        broadcast(row);
        checkAndSendAlarms(row);
        return true;
    }

    /** 广播已落库的实时设备数据（与 DeviceDataService.report 同消息契约）。 */
    private void broadcast(DeviceData row) {
        if (deviceDataProducer == null) {
            return;
        }
        try {
            DeviceDataMessage msg = new DeviceDataMessage(
                    row.getDeviceId(), row.getDataType(), row.getDataValue(),
                    row.getUnit(), row.getRecordedAt());
            deviceDataProducer.publish(msg);
        } catch (RuntimeException ex) {
            log.warn("MQTT ingest 广播失败（数据已落库，不阻塞）: deviceId={} type={}",
                    row.getDeviceId(), row.getDataType(), ex);
        }
    }

    /** 报警检测与消息投递；AlarmDetector 自身负责 alarm 表持久化。 */
    private void checkAndSendAlarms(DeviceData row) {
        try {
            List<AlarmVO> alarms = alarmDetector.check(
                    row.getDeviceId(), row.getDataType(), row.getDataValue());
            if (alarms == null || alarms.isEmpty() || alarmProducer == null) {
                return;
            }
            for (AlarmVO alarm : alarms) {
                if (alarm == null) {
                    continue;
                }
                AlarmMessage msg = new AlarmMessage(
                        row.getDeviceId(),
                        alarm.getAlarmType(),
                        alarm.getAlarmLevel(),
                        alarm.getAlarmMessage(),
                        row.getDataValue(),
                        row.getRecordedAt());
                alarmProducer.send(msg);
                alarmProducer.sendDelayCheck(msg);
            }
        } catch (RuntimeException ex) {
            log.warn("MQTT ingest 报警链路失败（数据已落库，不阻塞后续字段）: deviceId={} type={}",
                    row.getDeviceId(), row.getDataType(), ex);
        }
    }

    private LocalDateTime resolveRecordedAt(JsonNode tsNode) {
        String ts = text(tsNode);
        if (ts == null) {
            log.warn("MQTT ingest ts 缺失，使用服务端时间");
            return LocalDateTime.now();
        }
        try {
            // ISO-8601 带时区：保留 payload 的本地墙钟时间（如 12:00+08:00 → 12:00）
            return OffsetDateTime.parse(ts).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            log.warn("MQTT ingest ts 无法解析，使用服务端时间: ts={}", ts);
            return LocalDateTime.now();
        }
    }

    /**
     * Redis SETNX 幂等：null / 异常时降级为“放行写入”（宁愿重复不丢）。
     */
    private boolean acquireIdempotency(String key) {
        if (redis == null) {
            return true;
        }
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(key, "1", IDEMPOTENCY_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException ex) {
            log.warn("MQTT ingest 幂等 Redis SETNX 异常，降级不幂等仍写入: key={}", key, ex);
            return true;
        }
    }

    private String idempotencyKey(Long deviceId, LocalDateTime recordedAt, String dataType) {
        return IDEMPOTENCY_KEY_PREFIX + deviceId + ":"
                + recordedAt.format(IDEMPOTENCY_TIME) + ":" + dataType;
    }

    /** payload deviceCode 是权威来源；Topic 中的 device 段不一致仅告警不拒绝。 */
    private void checkTopicMismatch(String topic, String payloadDeviceCode) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        String[] segments = topic.split("/");
        if (segments.length < 3 || segments[2] == null || segments[2].isBlank()) {
            return;
        }
        if (!segments[2].equals(payloadDeviceCode)) {
            log.warn("MQTT ingest Topic device 与 payload deviceCode 不一致: topic={} payload={}",
                    topic, payloadDeviceCode);
        }
    }

    @Nullable
    private static String text(@Nullable JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Nullable
    private static BigDecimal parseNumber(JsonNode node) {
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                return new BigDecimal(node.asText());
            }
            return null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Map<String, FieldSpec> fieldMap() {
        Map<String, FieldSpec> map = new LinkedHashMap<>();
        map.put("current", new FieldSpec("current", "CURRENT", "A"));
        map.put("windingTemp", new FieldSpec("windingTemp", "TEMPERATURE", "°C"));
        map.put("pressure", new FieldSpec("pressure", "PRESSURE", "kPa"));
        map.put("speed", new FieldSpec("speed", "SPEED", "RPM"));
        return Collections.unmodifiableMap(map);
    }

    private static final class FieldSpec {
        private final String payloadName;
        private final String dataType;
        private final String unit;

        private FieldSpec(String payloadName, String dataType, String unit) {
            this.payloadName = payloadName;
            this.dataType = dataType;
            this.unit = unit;
        }
    }
}
