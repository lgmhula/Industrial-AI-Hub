package dev.reboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.AiDeviceStatusRequest;
import dev.reboot.dto.ai.AiDeviceStatusResult;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.tool.DeviceAiTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceStatusAgentService 单元测试 —— Function Calling 多轮循环 + 3 轮硬限 + 审计元数据。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ExtendWith(MockitoExtension.class)
class DeviceStatusAgentServiceTest {

    @Mock private ChatModel chatModel;
    @Mock private DeepSeekClient deepSeekClient;
    @Mock private DeviceMapper deviceMapper;
    @Mock private AlarmMapper alarmMapper;
    @Mock private SiteAccessService siteAccessService;

    private DeepSeekProperties properties;
    private DeviceStatusAgentService agentService;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setModel("deepseek-chat");
        properties.setMaxTokens(1024);
        properties.setTemperature(0.3);
        DeviceAiTools deviceAiTools = new DeviceAiTools(deviceMapper, alarmMapper, siteAccessService, new ObjectMapper());
        agentService = new DeviceStatusAgentService(chatModel, deepSeekClient, properties,
                deviceMapper, siteAccessService, deviceAiTools);
    }

    @Test
    void answer_withoutToolCalls_shouldMarkNotReferencedRealTime() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("设备当前在线，运行正常。"));

        AiDeviceStatusResult result = agentService.answer(request(1L, "设备状态如何？"), 7L);

        assertEquals("设备当前在线，运行正常。", result.getAnswer());
        assertEquals(0, result.getToolRounds());
        assertEquals(0, result.getToolCalls());
        assertFalse(result.isReferencedRealTime());
        assertFalse(result.isTruncated());
        verify(deepSeekClient).ensureAvailable();
        verify(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
    }

    @Test
    void answer_shouldExecuteToolCallsAndFeedResultsBack() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("call_1", "get_device_basic", "{\"deviceId\":1}"))
                .thenReturn(textResponse("设备在线，温度正常。"));

        AiDeviceStatusResult result = agentService.answer(request(1L, "查一下这台设备状态"), 7L);

        assertEquals("设备在线，温度正常。", result.getAnswer());
        assertEquals(1, result.getToolRounds());
        assertEquals(1, result.getToolCalls());
        assertTrue(result.isReferencedRealTime());
        assertFalse(result.isTruncated());
        assertEquals("get_device_basic", result.getToolTrace().get(0).getToolName());
        assertTrue(result.getToolTrace().get(0).isSuccess());

        // 第二次调用必须携带工具结果（ToolResponseMessage）且仍注册工具
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.times(2)).call(captor.capture());
        List<Message> secondMessages = captor.getAllValues().get(1).getInstructions();
        assertEquals(4, secondMessages.size());
        assertTrue(secondMessages.get(2) instanceof AssistantMessage);
        assertTrue(secondMessages.get(3) instanceof ToolResponseMessage);
        assertEquals(1, ((ToolResponseMessage) secondMessages.get(3)).getResponses().size());
        assertEquals("call_1", ((ToolResponseMessage) secondMessages.get(3)).getResponses().get(0).id());
        assertTrue(((ToolResponseMessage) secondMessages.get(3)).getResponses().get(0).responseData().contains("测试设备"));

        // 工具经 ToolContext 拿到 userId → 站点访问断言以 7 号用户执行（入口校验 + 工具内校验各一次）
        verify(siteAccessService, org.mockito.Mockito.times(2)).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
    }

    @Test
    void answer_unknownTool_shouldInjectErrorResponse() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("call_1", "no_such_tool", "{}"))
                .thenReturn(textResponse("无法查询，工具不可用。"));

        AiDeviceStatusResult result = agentService.answer(request(1L, "查询设备"), 7L);

        assertEquals(1, result.getToolCalls());
        assertFalse(result.getToolTrace().get(0).isSuccess());
        assertEquals("no_such_tool", result.getToolTrace().get(0).getToolName());
        assertTrue(result.getAnswer().contains("工具不可用"));
    }

    @Test
    void answer_hitsThreeRoundHardLimit_shouldTruncateAndFinalizeWithoutTools() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse("call_1", "get_device_basic", "{\"deviceId\":1}"))
                .thenReturn(toolCallResponse("call_2", "list_device_recent_alarms", "{\"deviceId\":1}"))
                .thenReturn(toolCallResponse("call_3", "list_device_recent_alarms", "{\"deviceId\":1}"))
                .thenReturn(toolCallResponse("call_4", "get_device_basic", "{\"deviceId\":1}"))
                .thenReturn(textResponse("基于已获取数据，设备运行正常。"));

        AiDeviceStatusResult result = agentService.answer(request(1L, "全面分析设备"), 7L);

        assertEquals(3, result.getToolRounds());
        // 第 4 次工具调用请求触发硬限 → 不再执行，实际执行 3 次工具调用
        assertEquals(3, result.getToolCalls());
        assertTrue(result.isReferencedRealTime());
        assertTrue(result.isTruncated());
        assertEquals("基于已获取数据，设备运行正常。", result.getAnswer());

        // 第 5 次（收尾）调用：无工具注册 + 含收尾提示
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.times(5)).call(captor.capture());
        Prompt finalPrompt = captor.getAllValues().get(4);
        assertTrue(finalPrompt.getInstructions().stream()
                .anyMatch(m -> m instanceof UserMessage u && u.getText().contains("轮次上限")));
        assertTrue(((ToolCallingChatOptions) finalPrompt.getOptions()).getToolCallbacks().isEmpty());
    }

    @Test
    void answer_noSiteAccess_shouldFailFastBeforeAnyLlmCall() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);

        assertThrows(BusinessException.class, () -> agentService.answer(request(1L, "查询设备"), 7L));
        verify(chatModel, never()).call(any(Prompt.class));
        // TD-033: 站点权限校验先于 AI 可用性检查 → ensureAvailable 不应被调用
        verify(deepSeekClient, never()).ensureAvailable();
    }

    @Test
    void answer_aiDisabled_shouldFailFastAfterResourceCheck() {
        // TD-033: 先校验设备存在 → 再校验站点权限 → 最后检查 AI 可用性
        when(deviceMapper.findById(1L)).thenReturn(device());
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "DeepSeek AI 服务未启用"))
                .when(deepSeekClient).ensureAvailable();

        assertThrows(BusinessException.class, () -> agentService.answer(request(1L, "查询设备"), 7L));
        verify(chatModel, never()).call(any(Prompt.class));
        verify(deviceMapper).findById(1L);
        verify(deepSeekClient).ensureAvailable();
    }

    private AiDeviceStatusRequest request(Long deviceId, String question) {
        AiDeviceStatusRequest req = new AiDeviceStatusRequest();
        req.setDeviceId(deviceId);
        req.setQuestion(question);
        return req;
    }

    private ChatResponse textResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    private ChatResponse toolCallResponse(String id, String toolName, String arguments) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("", Map.of(),
                        List.of(new AssistantMessage.ToolCall(id, "function", toolName, arguments))))))
                .build();
    }

    private Device device() {
        Device d = new Device();
        d.setId(1L);
        d.setSiteId(10L);
        d.setDeviceName("测试设备");
        d.setDeviceCode("DEV-001");
        d.setDeviceType("PLC");
        d.setLocation("1号车间");
        d.setStatus(1);
        d.setUpdatedAt(LocalDateTime.of(2026, 8, 29, 8, 0));
        return d;
    }
}
