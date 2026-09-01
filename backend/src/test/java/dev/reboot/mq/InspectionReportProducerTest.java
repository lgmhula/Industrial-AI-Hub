package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * InspectionReportProducer 单元测试（Day 85 Phase 1，ADR 0031）。
 *
 * <h3>验收要点（对齐 Phase 1 验收条件）</h3>
 * <ul>
 *   <li><b>Agent 不感知 SSE</b> —— 本测试不引入 SseEmitter / PushGateway 任何依赖，
 *       Producer 注入仅 {@link RabbitTemplate}（MQ 边界）；</li>
 *   <li><b>exchange / queue / DLQ 与 alarm 模式一致</b> —— 通过断言
 *       {@link MQConfig#INSPECTION_EXCHANGE} / {@link MQConfig#INSPECTION_ROUTING_KEY}
 *       复用 alarm 命名规范；</li>
 *   <li><b>Message 可 JSON 序列化</b> —— {@link InspectionReportMessage} 已 implements
 *       {@link java.io.Serializable}，且字段全部为 Jackson 友好类型
 *       （LocalDate/LocalDateTime/String/原始类型/List）；</li>
 *   <li><b>Producer 单元测试通过</b> —— 本测试覆盖正常投递 + 参数透传。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-31 (Day 85, Phase 1)
 */
@ExtendWith(MockitoExtension.class)
class InspectionReportProducerTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @Test
    void send_normalMessage_shouldCallConvertAndSendWithInspectionExchangeAndRoutingKey() {
        InspectionReportProducer producer = new InspectionReportProducer(rabbitTemplate);
        InspectionReportMessage message = sampleMessage();

        producer.send(message);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InspectionReportMessage> messageCaptor =
                ArgumentCaptor.forClass(InspectionReportMessage.class);
        verify(rabbitTemplate).convertAndSend(
                exchangeCaptor.capture(), routingKeyCaptor.capture(), messageCaptor.capture());
        assertEquals(MQConfig.INSPECTION_EXCHANGE, exchangeCaptor.getValue(),
                "应投递到 inspection.exchange（与 alarm.exchange 同模式 Direct）");
        assertEquals(MQConfig.INSPECTION_ROUTING_KEY, routingKeyCaptor.getValue(),
                "routing key 应为 inspection.report.new");
        assertSame(message, messageCaptor.getValue(),
                "应原样透传 InspectionReportMessage，不复制不修改");
    }

    @Test
    void send_fullFieldsMessage_shouldPreserveAllFieldsForConsumerRouting() {
        InspectionReportProducer producer = new InspectionReportProducer(rabbitTemplate);
        InspectionReportMessage message = sampleMessage();

        producer.send(message);

        ArgumentCaptor<InspectionReportMessage> captor =
                ArgumentCaptor.forClass(InspectionReportMessage.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                captor.capture());
        InspectionReportMessage captured = captor.getValue();
        assertEquals(LocalDate.of(2026, 8, 31), captured.getReportDate(),
                "reportDate 字段必须保留 —— Consumer 用于幂等键 inspection:{reportDate}:{siteId}");
        assertEquals("今日巡检正常，共 5 台设备。", captured.getReport());
        assertEquals(6, captured.getToolRounds(), "toolRounds 字段保留（审计摘要）");
        assertEquals(66, captured.getToolCalls(), "toolCalls 字段保留（审计摘要）");
        assertEquals(5, captured.getDeviceCount());
        assertEquals(2, captured.getAlarmCount());
        assertTrue(captured.isTruncated(), "truncated 字段保留（前端截断标记）");
        assertEquals(List.of(1L, 2L), captured.getSiteIds(),
                "siteIds 字段保留 —— Consumer 路由范围依据，ADR 0031 §5.1");
        assertEquals(1L, captured.getTriggeredByUserId(),
                "triggeredByUserId 字段保留（仅审计用，Consumer 不得据此越权路由）");
        assertEquals(LocalDateTime.of(2026, 8, 31, 21, 30), captured.getGeneratedAt());
    }

    @Test
    void send_emptySiteIdsList_shouldPreserveAdminAllSitesSemantics() {
        InspectionReportProducer producer = new InspectionReportProducer(rabbitTemplate);
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(LocalDate.of(2026, 8, 31));
        message.setReport("ADMIN 全站点巡检日报");
        message.setSiteIds(List.of()); // 空 List = 全站点（ADR 0031 §5.4 ADMIN 语义）

        producer.send(message);

        ArgumentCaptor<InspectionReportMessage> captor =
                ArgumentCaptor.forClass(InspectionReportMessage.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                captor.capture());
        assertTrue(captor.getValue().getSiteIds().isEmpty(),
                "空 siteIds List 必须原样透传 —— Consumer 识别为全站点 ADMIN 语义");
    }

    @Test
    void send_nullSiteIds_shouldNormalizeToEmptyList() {
        InspectionReportProducer producer = new InspectionReportProducer(rabbitTemplate);
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(LocalDate.of(2026, 8, 31));
        message.setReport("测试 null siteIds 归一化");
        message.setSiteIds(null); // Producer 不应 NPE，DTO setter 已归一化

        producer.send(message);

        ArgumentCaptor<InspectionReportMessage> captor =
                ArgumentCaptor.forClass(InspectionReportMessage.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                captor.capture());
        assertTrue(captor.getValue().getSiteIds().isEmpty(),
                "null siteIds 必须归一化为空 List（DTO setter 保证），避免 Consumer NPE");
    }

    /**
     * 构造完整字段的样本消息，便于多个测试复用。
     */
    private InspectionReportMessage sampleMessage() {
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(LocalDate.of(2026, 8, 31));
        message.setReport("今日巡检正常，共 5 台设备。");
        message.setToolRounds(6);
        message.setToolCalls(66);
        message.setDeviceCount(5);
        message.setAlarmCount(2);
        message.setTruncated(true);
        message.setSiteIds(List.of(1L, 2L));
        message.setTriggeredByUserId(1L);
        message.setGeneratedAt(LocalDateTime.of(2026, 8, 31, 21, 30));
        return message;
    }
}
