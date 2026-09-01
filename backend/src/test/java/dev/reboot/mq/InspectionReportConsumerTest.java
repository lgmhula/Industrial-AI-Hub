package dev.reboot.mq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InspectionReportConsumer 单元测试（Day 85 Phase 3 + Phase 4，ADR 0031 §5.1/§5.3/§6）。
 *
 * <h3>覆盖路径</h3>
 * <ul>
 *   <li>首次消息 —— SETNX 返回 true → 设置幂等键 → 调用 pushGateway.push → basicAck；</li>
 *   <li>重复消息 —— SETNX 返回 false → 跳过推送 → basicAck（防堆积）；</li>
 *   <li>多站点部分命中 —— 任一 siteId 首次即视为部分首次 → basicAck；</li>
 *   <li>Redis 故障 —— 抛异常 → basicNack(requeue=false) → DLQ；</li>
 *   <li>Phase 4 PushGateway 集成 —— 首次调用 push、重复不调用、null gateway 降级。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-31 (Day 85, Phase 3)；Phase 4 PushGateway 集成 (2026-09-01)
 */
@ExtendWith(MockitoExtension.class)
class InspectionReportConsumerTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private Channel channel;
    @Mock private InspectionPushGateway pushGateway;

    private InspectionReportConsumer consumer;

    @BeforeEach
    void setUp() {
        // 默认 consumer 不接 PushGateway（pushGateway=null），保持 Phase 3 6 个测试语义不变
        consumer = new InspectionReportConsumer(redis, null);
    }

    @Test
    void handleReport_firstMessage_adminAllSites_shouldSetKeyAndAck() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any()))
                .thenReturn(true); // 首次
        long deliveryTag = 100L;
        InspectionReportMessage message = adminAllSitesMessage();

        consumer.handleReport(message, channel, deliveryTag);

        // 幂等键应被设置：inspection:{reportDate}:all
        verify(valueOps).setIfAbsent(
                eq("inspection:2026-08-31:all"), eq("1"), any());
        // 首次应 ack（不 nack）
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(any(Long.class), eq(false), eq(false));
    }

    @Test
    void handleReport_duplicateMessage_adminAllSites_shouldSkipPushAndAck() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any()))
                .thenReturn(false); // 重复
        long deliveryTag = 101L;
        InspectionReportMessage message = adminAllSitesMessage();

        consumer.handleReport(message, channel, deliveryTag);

        // 重复消息应 ack（防堆积）但不重复推送
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(any(Long.class), eq(false), eq(false));
    }

    @Test
    void handleReport_multiSite_partialHit_shouldAckFirstTimeSites() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        // siteId=1 首次（true），siteId=2 重复（false）→ 部分首次 = 视为首次推送 + ack
        when(valueOps.setIfAbsent(eq("inspection:2026-08-31:1"), eq("1"), any()))
                .thenReturn(true);
        when(valueOps.setIfAbsent(eq("inspection:2026-08-31:2"), eq("1"), any()))
                .thenReturn(false);
        long deliveryTag = 102L;
        InspectionReportMessage message = multiSiteMessage(List.of(1L, 2L));

        consumer.handleReport(message, channel, deliveryTag);

        // 部分首次应 ack
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(any(Long.class), eq(false), eq(false));
    }

    @Test
    void handleReport_multiSite_allHit_shouldSkipPushAndAck() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        // 全部命中 = 重复
        when(valueOps.setIfAbsent(anyString(), eq("1"), any()))
                .thenReturn(false);
        long deliveryTag = 103L;
        InspectionReportMessage message = multiSiteMessage(List.of(1L, 2L));

        consumer.handleReport(message, channel, deliveryTag);

        // 全部重复应 ack
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(any(Long.class), eq(false), eq(false));
    }

    @Test
    void handleReport_redisFailure_shouldNackAndRouteToDLQ() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Redis unreachable"));
        long deliveryTag = 104L;
        InspectionReportMessage message = adminAllSitesMessage();

        consumer.handleReport(message, channel, deliveryTag);

        // Redis 故障应 nack(requeue=false) → DLQ
        verify(channel).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(any(Long.class), eq(false));
    }

    @Test
    void handleReport_ackFailure_shouldLogButNotRethrow() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any())).thenReturn(true);
        long deliveryTag = 105L;
        // basicAck 抛 IOException 不应冒泡（Consumer 内部 catch）
        org.mockito.Mockito.doThrow(new IOException("channel closed"))
                .when(channel).basicAck(deliveryTag, false);
        InspectionReportMessage message = adminAllSitesMessage();

        // 不抛异常 = 测试通过（ack 失败已被 catch）
        consumer.handleReport(message, channel, deliveryTag);

        verify(valueOps, atLeastOnce()).setIfAbsent(anyString(), eq("1"), any());
    }

    // ── Phase 4 PushGateway 集成测试 ──────────────────────────────────────

    @Test
    void handleReport_firstMessage_withPushGateway_shouldCallPushAndAck() throws Exception {
        // 构造带 mock PushGateway 的 consumer（非默认 setUp 的 null 版本）
        InspectionReportConsumer consumerWithGateway = new InspectionReportConsumer(redis, pushGateway);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true); // 首次
        long deliveryTag = 200L;
        InspectionReportMessage message = adminAllSitesMessage();

        consumerWithGateway.handleReport(message, channel, deliveryTag);

        // 首次消息应调用 pushGateway.push(message)
        verify(pushGateway).push(message);
        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(any(Long.class), eq(false), eq(false));
    }

    @Test
    void handleReport_duplicateMessage_withPushGateway_shouldNotCallPush() throws Exception {
        InspectionReportConsumer consumerWithGateway = new InspectionReportConsumer(redis, pushGateway);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any())).thenReturn(false); // 重复
        long deliveryTag = 201L;
        InspectionReportMessage message = adminAllSitesMessage();

        consumerWithGateway.handleReport(message, channel, deliveryTag);

        // 重复消息不应调用 push（幂等短路）
        verify(pushGateway, never()).push(any());
        verify(channel).basicAck(deliveryTag, false); // 重复仍 ack 防堆积
    }

    private InspectionReportMessage adminAllSitesMessage() {
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(LocalDate.of(2026, 8, 31));
        message.setReport("今日巡检正常，共 5 台设备。");
        message.setToolRounds(6);
        message.setToolCalls(66);
        message.setDeviceCount(5);
        message.setAlarmCount(2);
        message.setTruncated(true);
        message.setSiteIds(List.of()); // ADMIN 全站点语义
        message.setTriggeredByUserId(1L);
        message.setGeneratedAt(java.time.LocalDateTime.of(2026, 8, 31, 21, 30));
        return message;
    }

    private InspectionReportMessage multiSiteMessage(List<Long> siteIds) {
        InspectionReportMessage message = adminAllSitesMessage();
        message.setSiteIds(siteIds);
        return message;
    }
}
