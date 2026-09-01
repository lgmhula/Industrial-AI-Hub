package dev.reboot.service;

import dev.reboot.agent.AgentRunResult;
import dev.reboot.agent.ToolCallingAgent;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.dto.ai.AiInspectionReportResult;
import dev.reboot.dto.ai.AiToolCallTrace;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mcp.McpClientService;
import dev.reboot.mcp.McpInspectionSession;
import dev.reboot.mq.InspectionReportMessage;
import dev.reboot.mq.InspectionReportProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpInspectionAgentService 单元测试（ADR 0030 + Day 85 Phase 2 ADR 0031）。
 *
 * <h3>Day 85 Phase 2 新增覆盖</h3>
 * <ul>
 *   <li>{@code generate()} 末尾投递 InspectionReportMessage —— 验证 send 被调用 + 字段映射；</li>
 *   <li>MQ 异常降级不阻塞 —— AmqpException 被 catch，result 仍返回；</li>
 *   <li>null Producer（test profile 模拟）—— 跳过投递不 NPE。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-30, Phase 2 接入测试 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class McpInspectionAgentServiceTest {

    @Mock private DeepSeekClient deepSeekClient;
    @Mock private McpClientService mcpClientService;
    @Mock private ToolCallingAgent toolCallingAgent;
    @Mock private McpInspectionSession session;
    @Mock private InspectionReportProducer inspectionReportProducer;

    private McpInspectionAgentService service;

    @BeforeEach
    void setUp() {
        service = new McpInspectionAgentService(deepSeekClient, mcpClientService,
                toolCallingAgent, inspectionReportProducer);
    }

    @Test
    void generate_shouldRunAgentThroughMcpSessionAndMapResult() throws Exception {
        when(mcpClientService.openInspectionSession()).thenReturn(session);
        when(session.toolCallbacks()).thenReturn(new ToolCallback[0]);
        when(toolCallingAgent.run(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(new AgentRunResult("今日巡检正常，共 2 台设备。", 3, 6, true, false,
                        List.of(new AiToolCallTrace("mcp_list_devices", true))));
        when(session.deviceCount()).thenReturn(2);
        when(session.alarmCount()).thenReturn(3);

        AiInspectionReportResult result = service.generate();

        assertEquals("今日巡检正常，共 2 台设备。", result.getReport());
        assertEquals(2, result.getDeviceCount());
        assertEquals(3, result.getAlarmCount());
        assertEquals(3, result.getToolRounds());
        assertEquals(6, result.getToolCalls());
        assertFalse(result.isTruncated());
        assertEquals(1, result.getToolTrace().size());
        verify(deepSeekClient).ensureAvailable();
        verify(session).close();
    }

    @Test
    void generate_deepSeekDisabled_shouldFailBeforeOpeningSession() {
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "DeepSeek AI 服务未启用"))
                .when(deepSeekClient).ensureAvailable();

        assertThrows(BusinessException.class, service::generate);

        verify(mcpClientService, never()).openInspectionSession();
        verify(toolCallingAgent, never()).run(anyString(), anyString(), any(), any(), anyInt());
        verify(inspectionReportProducer, never()).send(any());
    }

    /**
     * Day 85 Phase 2：generate() 末尾应投递 InspectionReportMessage，
     * 字段对齐 ADR 0031 §3.1 消息契约。
     */
    @Test
    void generate_shouldDispatchInspectionReportMessageWithMappedFields() throws Exception {
        when(mcpClientService.openInspectionSession()).thenReturn(session);
        when(session.toolCallbacks()).thenReturn(new ToolCallback[0]);
        when(toolCallingAgent.run(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(new AgentRunResult("日报正文", 6, 66, true, true,
                        List.of()));
        when(session.deviceCount()).thenReturn(5);
        when(session.alarmCount()).thenReturn(2);

        AiInspectionReportResult result = service.generate();

        ArgumentCaptor<InspectionReportMessage> captor =
                ArgumentCaptor.forClass(InspectionReportMessage.class);
        verify(inspectionReportProducer).send(captor.capture());
        InspectionReportMessage msg = captor.getValue();
        assertEquals(result.getReportDate(), msg.getReportDate(),
                "reportDate 必须与 result 一致（幂等键组成）");
        assertEquals("日报正文", msg.getReport());
        assertEquals(6, msg.getToolRounds(), "toolRounds 透传（审计摘要）");
        assertEquals(66, msg.getToolCalls(), "toolCalls 透传（审计摘要）");
        assertEquals(5, msg.getDeviceCount());
        assertEquals(2, msg.getAlarmCount());
        assertTrue(msg.isTruncated(), "truncated 透传（前端截断标记）");
        assertTrue(msg.getSiteIds().isEmpty(),
                "siteIds 为空 List —— ADMIN 全站点语义（ADR 0031 §5.4）");
        // triggeredByUserId 在 Phase 6 由 Controller 注入，此处为 null
        // generatedAt 由 Agent 在 toMessage 时设置，非 null
        assertNotNull(msg.getGeneratedAt());
    }

    /**
     * Day 85 Phase 2 / ADR 0031 §6 RabbitMQ 异常行：
     * Producer.send 抛 AmqpException 时，generate() 仍正常返回 result，
     * <b>不阻塞 Agent 主流程</b>。
     */
    @Test
    void generate_mqFailure_shouldNotBlockResultReturn() throws Exception {
        when(mcpClientService.openInspectionSession()).thenReturn(session);
        when(session.toolCallbacks()).thenReturn(new ToolCallback[0]);
        when(toolCallingAgent.run(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(new AgentRunResult("MQ 故障下的日报", 1, 1, true, false, List.of()));
        when(session.deviceCount()).thenReturn(1);
        when(session.alarmCount()).thenReturn(0);
        doThrow(new AmqpException("broker unreachable"))
                .when(inspectionReportProducer).send(any(InspectionReportMessage.class));

        AiInspectionReportResult result = service.generate();

        // 关键断言：MQ 异常不阻塞，result 仍正常返回
        assertEquals("MQ 故障下的日报", result.getReport());
        assertEquals(1, result.getDeviceCount());
        verify(inspectionReportProducer).send(any(InspectionReportMessage.class));
    }

    /**
     * Day 85 Phase 2：test profile 下 Producer 为 null（@Profile("!test")），
     * generate() 应跳过投递，不 NPE，不影响上下文加载与主流程。
     */
    @Test
    void generate_nullProducer_shouldSkipDispatchAndReturnResult() throws Exception {
        // 模拟 test profile：Producer 为 null
        service = new McpInspectionAgentService(deepSeekClient, mcpClientService,
                toolCallingAgent, null);
        when(mcpClientService.openInspectionSession()).thenReturn(session);
        when(session.toolCallbacks()).thenReturn(new ToolCallback[0]);
        when(toolCallingAgent.run(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(new AgentRunResult("null Producer 下的日报", 1, 1, true, false, List.of()));
        when(session.deviceCount()).thenReturn(1);
        when(session.alarmCount()).thenReturn(0);

        AiInspectionReportResult result = service.generate();

        assertEquals("null Producer 下的日报", result.getReport());
        assertEquals(1, result.getDeviceCount());
        // 无 Producer 可调用，验证不抛异常即可
    }
}
