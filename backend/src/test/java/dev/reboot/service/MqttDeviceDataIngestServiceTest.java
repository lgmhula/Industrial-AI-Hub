package dev.reboot.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MQTT 遥测入库服务单元测试（Day 96，ADR 0034）。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>完整 4 字段 Payload：全部落库 + 广播 + 幂等键按字段粒度写入；</li>
 *   <li>QoS1 重复（Redis SETNX false）：跳过落库/广播；</li>
 *   <li>Redis 异常：降级不幂等，仍落库；</li>
 *   <li>无 Redis（constructor null）：仍落库；</li>
 *   <li>设备编码不存在：返回 false 不抛异常；</li>
 *   <li>ts 非法：回退服务端时间；</li>
 *   <li>ts 带 +08:00：保留本地墙钟时间；</li>
 *   <li>非法 JSON：返回 false；</li>
 *   <li>非数值 / 未知字段：不阻塞其他字段落库；</li>
 *   <li>AlarmProducer 为 null：报警检测正常、消息投递跳过；</li>
 *   <li>AlarmDetector 异常：数据已落库，不阻塞后续字段；</li>
 *   <li>DeviceDataMapper 异常：单字段降级，不阻塞后续字段。</li>
 * </ol>
 * </p>
 *
 * @author AI 助手
 * @since 2026-09-05
 */
@ExtendWith(MockitoExtension.class)
class MqttDeviceDataIngestServiceTest {

    private static final String TOPIC = "plc/PLANT_A/PLC-M-001/telemetry";
    private static final String TS = "2026-09-05T12:00:00+08:00";

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private AlarmDetector alarmDetector;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DeviceDataProducer deviceDataProducer;
    @Mock private AlarmProducer alarmProducer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttDeviceDataIngestService service;

    @BeforeEach
    void setUp() {
        // lenient：部分测试走不到 Redis（unknown/invalidJson/noRedis 等）
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = buildService(redis, deviceDataProducer, alarmProducer);
    }

    @Test
    void ingest_fullPayload_shouldPersistBroadcastAndBuildFieldKeys() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        boolean persisted = service.ingest(TOPIC, fullPayload());

        assertTrue(persisted, "4 个顶层工程值字段应全部落库");
        ArgumentCaptor<DeviceData> rows = ArgumentCaptor.forClass(DeviceData.class);
        verify(deviceDataMapper, times(4)).insert(rows.capture());
        verify(deviceDataProducer, times(4)).publish(any(DeviceDataMessage.class));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(4)).setIfAbsent(keys.capture(), eq("1"), any());
        assertEquals(List.of(
                "mqtt:101:20260905120000:CURRENT",
                "mqtt:101:20260905120000:TEMPERATURE",
                "mqtt:101:20260905120000:PRESSURE",
                "mqtt:101:20260905120000:SPEED"), keys.getAllValues());
    }

    @Test
    void ingest_redisDuplicate_shouldSkipInsertAndBroadcast() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.FALSE);

        boolean persisted = service.ingest(TOPIC, payload(TS, "\"windingTemp\":32.5"));

        assertFalse(persisted, "幂等命中应视为未新增");
        verify(deviceDataMapper, never()).insert(any(DeviceData.class));
        verify(deviceDataProducer, never()).publish(any(DeviceDataMessage.class));
        verify(alarmDetector, never()).check(any(), any(), any());
    }

    @Test
    void ingest_redisException_shouldFallbackToWrite() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any()))
                .thenThrow(new RuntimeException("Redis connection reset"));
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        boolean persisted = service.ingest(TOPIC, payload(TS, "\"windingTemp\":32.5"));

        assertTrue(persisted, "Redis 异常降级为不幂等，仍应写入");
        verify(deviceDataMapper).insert(any(DeviceData.class));
        verify(deviceDataProducer).publish(any(DeviceDataMessage.class));
    }

    @Test
    void ingest_noRedis_shouldStillWrite() {
        MqttDeviceDataIngestService noRedis = buildService(null, deviceDataProducer, alarmProducer);
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        boolean persisted = noRedis.ingest(TOPIC, payload(TS, "\"windingTemp\":32.5"));

        assertTrue(persisted, "无 Redis Bean 也应正常落库");
        verify(deviceDataMapper).insert(any(DeviceData.class));
        verify(valueOps, never()).setIfAbsent(anyString(), eq("1"), any());
    }

    @Test
    void ingest_unknownDevice_shouldReturnFalseWithoutThrowing() {
        when(deviceMapper.findByCode("PLC-SIM-999")).thenReturn(null);

        boolean persisted = service.ingest("plc/PLANT_A/PLC-SIM-999/telemetry",
                payload(TS, "\"windingTemp\":32.5", "PLC-SIM-999"));

        assertFalse(persisted, "设备不存在应跳过且不抛异常");
        verify(deviceDataMapper, never()).insert(any(DeviceData.class));
    }

    @Test
    void ingest_invalidTs_shouldFallbackToServerTime() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        boolean persisted = service.ingest(TOPIC, payload("not-a-time", "\"windingTemp\":32.5"));

        assertTrue(persisted);
        ArgumentCaptor<DeviceData> row = ArgumentCaptor.forClass(DeviceData.class);
        verify(deviceDataMapper).insert(row.capture());
        LocalDateTime recordedAt = row.getValue().getRecordedAt();
        assertNotNull(recordedAt, "非法 ts 应回退为服务端时间");
        assertTrue(recordedAt.isAfter(LocalDateTime.now().minusSeconds(2)),
                "回退时间应接近当前时间");
        assertFalse(recordedAt.isAfter(LocalDateTime.now().plusSeconds(1)),
                "回退时间不应在未来");
    }

    @Test
    void ingest_zonedTs_shouldKeepLocalWallClock() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        service.ingest(TOPIC, payload(TS, "\"windingTemp\":32.5"));

        ArgumentCaptor<DeviceData> row = ArgumentCaptor.forClass(DeviceData.class);
        verify(deviceDataMapper).insert(row.capture());
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 0, 0), row.getValue().getRecordedAt(),
                "+08:00 的 12:00 应原样保留为本地墙钟 12:00");
    }

    @Test
    void ingest_invalidJson_shouldReturnFalseWithoutLookups() {
        boolean persisted = service.ingest(TOPIC, "{not-json");

        assertFalse(persisted, "非法 JSON 应返回 false");
        verify(deviceMapper, never()).findByCode(anyString());
        verify(deviceDataMapper, never()).insert(any(DeviceData.class));
    }

    @Test
    void ingest_mixedBadField_shouldNotBlockGoodField() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        boolean persisted = service.ingest(TOPIC,
                payload(TS, "\"current\":\"abc\",\"unknownPoint\":9.9,\"windingTemp\":32.5"));

        assertTrue(persisted, "非法数值字段不应阻塞合法字段");
        ArgumentCaptor<DeviceData> rows = ArgumentCaptor.forClass(DeviceData.class);
        verify(deviceDataMapper).insert(rows.capture());
        assertEquals("TEMPERATURE", rows.getValue().getDataType());
        verify(valueOps, times(1)).setIfAbsent(anyString(), eq("1"), any());
    }

    @Test
    void ingest_alarmProducerNull_shouldCheckAlarmsAndSkipSend() {
        MqttDeviceDataIngestService noAlarmProducer =
                buildService(redis, deviceDataProducer, null);
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        AlarmVO alarm = alarm("OVER_TEMP", 2, "设备 101 温度过高");
        when(alarmDetector.check(eq(101L), eq("TEMPERATURE"), any()))
                .thenReturn(List.of(alarm));

        boolean persisted = noAlarmProducer.ingest(TOPIC, payload(TS, "\"windingTemp\":45.2"));

        assertTrue(persisted);
        verify(alarmDetector).check(eq(101L), eq("TEMPERATURE"), eq(new BigDecimal("45.2")));
        verify(alarmProducer, never()).send(any(AlarmMessage.class));
        verify(alarmProducer, never()).sendDelayCheck(any(AlarmMessage.class));
    }

    @Test
    void ingest_alarmDetectorException_shouldNotBlockLaterFields() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmDetector.check(any(), any(), any()))
                .thenThrow(new RuntimeException("alarm DB write failed"))
                .thenReturn(Collections.emptyList());

        boolean persisted = service.ingest(TOPIC, fullPayload());

        assertTrue(persisted, "报警链路异常不应阻塞后续字段落库");
        verify(deviceDataMapper, times(4)).insert(any(DeviceData.class));
        verify(deviceDataProducer, times(4)).publish(any(DeviceDataMessage.class));
        verify(alarmProducer, never()).send(any(AlarmMessage.class));
    }

    @Test
    void ingest_mapperException_shouldNotBlockLaterFields() {
        when(deviceMapper.findByCode("PLC-M-001")).thenReturn(device(101L, "PLC-M-001"));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(deviceDataMapper.insert(any(DeviceData.class)))
                .thenThrow(new RuntimeException("DB insert failed"))
                .thenReturn(1, 1, 1);
        when(alarmDetector.check(any(), any(), any())).thenReturn(Collections.emptyList());

        boolean persisted = service.ingest(TOPIC, fullPayload());

        assertTrue(persisted, "单字段落库异常应降级，后续字段继续写入");
        verify(deviceDataMapper, times(4)).insert(any(DeviceData.class));
        verify(deviceDataProducer, times(3)).publish(any(DeviceDataMessage.class));
    }

    // —— Helpers ——

    private MqttDeviceDataIngestService buildService(StringRedisTemplate redisTemplate,
                                                     DeviceDataProducer dataProducer,
                                                     AlarmProducer alarmProd) {
        return new MqttDeviceDataIngestService(
                objectMapper, deviceMapper, deviceDataMapper, alarmDetector,
                redisTemplate, dataProducer, alarmProd);
    }

    private Device device(Long id, String code) {
        Device d = new Device();
        d.setId(id);
        d.setDeviceCode(code);
        d.setSiteId(1L);
        d.setDeviceName("PLC");
        return d;
    }

    private AlarmVO alarm(String type, int level, String message) {
        AlarmVO vo = new AlarmVO();
        vo.setAlarmType(type);
        vo.setAlarmLevel(level);
        vo.setAlarmMessage(message);
        return vo;
    }

    private String fullPayload() {
        return payload(TS,
                "\"current\":12.6,\"windingTemp\":32.5,\"pressure\":102.5,"
                        + "\"speed\":1480.0,\"status\":\"running\","
                        + "\"registerSnapshot\":[]");
    }

    private String payload(String ts, String fields) {
        return payload(ts, fields, "PLC-M-001");
    }

    private String payload(String ts, String fields, String deviceCode) {
        return "{\"deviceCode\":\"" + deviceCode + "\",\"siteCode\":\"PLANT_A\","
                + "\"ts\":\"" + ts + "\",\"version\":\"1.0\"," + fields + "}";
    }
}
