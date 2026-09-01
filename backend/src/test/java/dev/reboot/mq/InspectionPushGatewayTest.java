package dev.reboot.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InspectionPushGateway 单元测试（Day 85 Phase 4，ADR 0031 §5.3）。
 *
 * <h3>覆盖路径</h3>
 * <ul>
 *   <li>无订阅 —— findAll 返回空 → 跳过推送，不报错；</li>
 *   <li>站点匹配 —— 会话 siteIds 含日报 siteIds 任一 → 推送；</li>
 *   <li>站点不匹配 —— 交集为空 → 跳过该会话；</li>
 *   <li>ADMIN 会话 —— siteIds 为空接收所有日报；</li>
 *   <li>IOException —— emitter.send 抛 IO 异常 → 移除失效会话；</li>
 *   <li>IllegalStateException —— emitter 已关闭 → 移除失效会话；</li>
 *   <li>混合会话 —— 单个失败不阻塞其他会话推送。</li>
 * </ul>
 *
 * <p>Mock 策略：{@link SseEmitter} 被 Mockito mock 化（{@code send} 方法非 final，
 * 可 stub 抛异常）；{@link SseEmitterRegistry} 同样 mock 化以验证 {@code remove} 调用。
 * {@link SseEmitterSession} 用真实对象构造（注入 mock emitter）。</p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 4)
 */
@ExtendWith(MockitoExtension.class)
class InspectionPushGatewayTest {

    @Mock private SseEmitterRegistry registry;

    private InspectionPushGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new InspectionPushGateway(registry);
    }

    @Test
    void push_noSessions_shouldSkipAndNotRemove() {
        when(registry.findAll()).thenReturn(List.of());
        InspectionReportMessage message = adminMessage();

        gateway.push(message);

        // 无会话时不应尝试移除任何 userId
        verify(registry, never()).remove(any(Long.class));
    }

    @Test
    void push_matchingSite_shouldSendAndNotRemove() throws Exception {
        SseEmitter mockEmitter = Mockito.mock(SseEmitter.class);
        SseEmitterSession session = new SseEmitterSession(1L, List.of(10L), mockEmitter);
        when(registry.findAll()).thenReturn(List.of(session));
        InspectionReportMessage message = multiSiteMessage(List.of(10L, 20L));

        gateway.push(message);

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(registry, never()).remove(any(Long.class));
    }

    @Test
    void push_nonMatchingSite_shouldSkipSession() throws Exception {
        SseEmitter mockEmitter = Mockito.mock(SseEmitter.class);
        // 会话只含 siteId=30，日报 siteIds=[10,20]，交集为空
        SseEmitterSession session = new SseEmitterSession(1L, List.of(30L), mockEmitter);
        when(registry.findAll()).thenReturn(List.of(session));
        InspectionReportMessage message = multiSiteMessage(List.of(10L, 20L));

        gateway.push(message);

        verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(registry, never()).remove(any(Long.class));
    }

    @Test
    void push_adminSession_receivesAllReports() throws Exception {
        SseEmitter mockEmitter = Mockito.mock(SseEmitter.class);
        // ADMIN：siteIds 为空 → 接收所有日报
        SseEmitterSession adminSession = new SseEmitterSession(1L, List.of(), mockEmitter);
        when(registry.findAll()).thenReturn(List.of(adminSession));
        InspectionReportMessage message = multiSiteMessage(List.of(10L)); // 非全站点日报

        gateway.push(message);

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void push_adminReport_receivedByAllSessions() throws Exception {
        // 日报 siteIds 为空（ADMIN 全站点语义）→ 所有会话都能接收
        SseEmitter emitter1 = Mockito.mock(SseEmitter.class);
        SseEmitter emitter2 = Mockito.mock(SseEmitter.class);
        SseEmitterSession session1 = new SseEmitterSession(1L, List.of(10L), emitter1);
        SseEmitterSession session2 = new SseEmitterSession(2L, List.of(20L), emitter2);
        when(registry.findAll()).thenReturn(List.of(session1, session2));
        InspectionReportMessage message = adminMessage(); // siteIds=[]

        gateway.push(message);

        verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void push_ioException_shouldRemoveFailedSession() throws Exception {
        SseEmitter mockEmitter = Mockito.mock(SseEmitter.class);
        Mockito.doThrow(new java.io.IOException("connection lost"))
                .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        SseEmitterSession session = new SseEmitterSession(1L, List.of(10L), mockEmitter);
        when(registry.findAll()).thenReturn(List.of(session));
        InspectionReportMessage message = multiSiteMessage(List.of(10L));

        gateway.push(message);

        // 失效会话应被移除
        verify(registry).remove(1L);
    }

    @Test
    void push_illegalStateException_shouldRemoveFailedSession() throws Exception {
        SseEmitter mockEmitter = Mockito.mock(SseEmitter.class);
        Mockito.doThrow(new IllegalStateException("emitter already completed"))
                .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        SseEmitterSession session = new SseEmitterSession(1L, List.of(10L), mockEmitter);
        when(registry.findAll()).thenReturn(List.of(session));
        InspectionReportMessage message = multiSiteMessage(List.of(10L));

        gateway.push(message);

        verify(registry).remove(1L);
    }

    @Test
    void push_mixedSessions_failureDoesNotBlockOthers() throws Exception {
        SseEmitter failEmitter = Mockito.mock(SseEmitter.class);
        Mockito.doThrow(new java.io.IOException("fail"))
                .when(failEmitter).send(any(SseEmitter.SseEventBuilder.class));
        SseEmitter okEmitter = Mockito.mock(SseEmitter.class);
        SseEmitterSession failSession = new SseEmitterSession(1L, List.of(10L), failEmitter);
        SseEmitterSession okSession = new SseEmitterSession(2L, List.of(10L), okEmitter);
        when(registry.findAll()).thenReturn(List.of(failSession, okSession));
        InspectionReportMessage message = multiSiteMessage(List.of(10L));

        gateway.push(message);

        // 失败会话移除，成功会话推送 —— 单点失败不阻塞其他用户
        verify(registry).remove(1L);
        verify(okEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ── 测试数据辅助 ──────────────────────────────────────────────────

    private InspectionReportMessage adminMessage() {
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(LocalDate.of(2026, 9, 1));
        message.setReport("今日巡检正常，共 5 台设备。");
        message.setToolRounds(6);
        message.setToolCalls(66);
        message.setDeviceCount(5);
        message.setAlarmCount(2);
        message.setTruncated(true);
        message.setSiteIds(List.of()); // ADMIN 全站点
        message.setTriggeredByUserId(1L);
        message.setGeneratedAt(java.time.LocalDateTime.of(2026, 9, 1, 9, 0));
        return message;
    }

    private InspectionReportMessage multiSiteMessage(List<Long> siteIds) {
        InspectionReportMessage message = adminMessage();
        message.setSiteIds(siteIds);
        return message;
    }
}
