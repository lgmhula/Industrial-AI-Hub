package dev.reboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.AiAlarmSummary;
import dev.reboot.dto.ai.AiChatRequest;
import dev.reboot.dto.ai.AiChatResult;
import dev.reboot.dto.ai.AiDeviceDiagnosis;
import dev.reboot.dto.ai.KnowledgeChunk;
import dev.reboot.dto.ai.RagAnswerResult;
import dev.reboot.dto.ai.DeepSeekChatRequest;
import dev.reboot.dto.ai.DeepSeekChatResponse;
import dev.reboot.dto.ai.DeepSeekChoice;
import dev.reboot.dto.ai.DeepSeekMessage;
import dev.reboot.dto.ai.DeepSeekUsage;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiService 单元测试 —— 提示词编排 + 结构化 JSON 解析 + 站点作用域。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClientRequestSpec requestSpec;
    @Mock private CallResponseSpec callSpec;
    @Mock private DeepSeekClient deepSeekClient;
    @Mock private AlarmMapper alarmMapper;
    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private SiteAccessService siteAccessService;
    @Mock private RagRetrievalService ragRetrievalService;

    private DeepSeekProperties properties;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setModel("deepseek-chat");
        properties.setMaxTokens(1024);
        properties.setTemperature(0.3);
        aiService = new AiService(chatClient, deepSeekClient, properties, new ObjectMapper(),
                alarmMapper, deviceMapper, deviceDataMapper, siteAccessService, ragRetrievalService);
    }

    @Test
    void chat_shouldSendSystemAndUserMessages() {
        when(deepSeekClient.chatCompletion(any(DeepSeekChatRequest.class)))
                .thenReturn(responseWith("你好，我是 AI"));

        AiChatRequest request = new AiChatRequest();
        request.setMessage("设备温度异常");
        request.setSystemPrompt("你是运维助手");
        AiChatResult result = aiService.chat(request);

        ArgumentCaptor<DeepSeekChatRequest> captor = ArgumentCaptor.forClass(DeepSeekChatRequest.class);
        verify(deepSeekClient).chatCompletion(captor.capture());
        assertEquals("system", captor.getValue().getMessages().get(0).getRole());
        assertEquals("user", captor.getValue().getMessages().get(1).getRole());
        assertEquals("deepseek-chat", captor.getValue().getModel());
        assertEquals("你好，我是 AI", result.getContent());
        assertEquals(17, result.getTotalTokens());
        assertNull(captor.getValue().getResponseFormat());
    }

    @Test
    void chat_emptyResponse_shouldThrowServiceUnavailable() {
        when(deepSeekClient.chatCompletion(any(DeepSeekChatRequest.class)))
                .thenReturn(new DeepSeekChatResponse());

        AiChatRequest request = new AiChatRequest();
        request.setMessage("test");
        BusinessException e = assertThrows(BusinessException.class, () -> aiService.chat(request));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void summarizeAlarm_shouldParseStructuredJson() {
        stubChatClient();
        when(alarmMapper.findById(1L)).thenReturn(alarm());
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(callSpec.content()).thenReturn("""
                {"summary":"传感器读数超过上限","possibleCauses":["探头老化"],"suggestedActions":["现场检查"],"priority":"高"}
                """);

        AiAlarmSummary result = aiService.summarizeAlarm(1L, 1L);

        assertEquals("传感器读数超过上限", result.getSummary());
        assertEquals(List.of("探头老化"), result.getPossibleCauses());
        assertEquals("高", result.getPriority());
        verify(siteAccessService).assertSiteAccess(1L, 10L, RoleEnum.VIEWER);
        verify(chatClient).prompt();
        verify(requestSpec).system(anyString());
        verify(requestSpec).user(contains("测试设备"));
        verify(requestSpec).user(contains("温度超过 80°C"));
        verify(deepSeekClient).ensureAvailable();
    }

    @Test
    void summarizeAlarm_markdownFence_shouldBeUnwrapped() {
        stubChatClient();
        when(alarmMapper.findById(1L)).thenReturn(alarm());
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(callSpec.content()).thenReturn("```json\n"
                + "{\"summary\":\"告警摘要\",\"possibleCauses\":[],\"suggestedActions\":[],\"priority\":\"中\"}\n"
                + "```");

        AiAlarmSummary result = aiService.summarizeAlarm(1L, 1L);

        assertEquals("告警摘要", result.getSummary());
        assertEquals("中", result.getPriority());
    }

    @Test
    void summarizeAlarm_invalidJson_shouldFallbackToText() {
        stubChatClient();
        when(alarmMapper.findById(1L)).thenReturn(alarm());
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(callSpec.content()).thenReturn("模型返回的纯文本摘要");

        AiAlarmSummary result = aiService.summarizeAlarm(1L, 1L);

        assertEquals("模型返回的纯文本摘要", result.getSummary());
        assertEquals(0, result.getPossibleCauses().size());
    }

    @Test
    void summarizeAlarm_noSiteAccess_shouldNotCallAi() {
        when(alarmMapper.findById(1L)).thenReturn(alarm());
        when(deviceMapper.findById(1L)).thenReturn(device());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(1L, 10L, RoleEnum.VIEWER);

        assertThrows(BusinessException.class, () -> aiService.summarizeAlarm(1L, 1L));
        verify(deepSeekClient, never()).chatCompletion(any());
        verify(chatClient, never()).prompt();
    }

    @Test
    void diagnoseDevice_shouldParseStructuredJson() {
        stubChatClient();
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(deviceDataMapper.findByDeviceId(1L)).thenReturn(List.of(deviceData()));
        when(alarmMapper.findByDeviceId(1L)).thenReturn(List.of(alarm()));
        when(callSpec.content()).thenReturn("""
                {"healthLevel":"关注","summary":"温度偏高","issues":["连续高温"],"suggestedActions":["清洗散热片"]}
                """);

        AiDeviceDiagnosis result = aiService.diagnoseDevice(1L, 1L);

        assertEquals("关注", result.getHealthLevel());
        assertEquals(List.of("连续高温"), result.getIssues());
        verify(siteAccessService).assertSiteAccess(1L, 10L, RoleEnum.VIEWER);
        verify(requestSpec).user(contains("TEMPERATURE=82.5°C"));
        verify(requestSpec).user(contains("OVER_TEMP/等级2/状态0"));
    }

    @Test
    void summarizeAlarm_emptyAiContent_shouldThrowServiceUnavailable() {
        stubChatClient();
        when(alarmMapper.findById(1L)).thenReturn(alarm());
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(callSpec.content()).thenReturn("   ");

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiService.summarizeAlarm(1L, 1L));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void answerWithRag_shouldInjectRetrievedContextAndReturnAnswer() {
        stubChatClient();
        when(ragRetrievalService.retrieve("设备手册", 5))
                .thenReturn(List.of(knowledgeChunk()));
        when(callSpec.content()).thenReturn("请检查散热片并重新校准传感器。");

        RagAnswerResult result = aiService.answerWithRag("设备手册");

        assertEquals("请检查散热片并重新校准传感器。", result.getAnswer());
        assertEquals(1, result.getSources().size());
        verify(deepSeekClient).ensureAvailable();
        verify(requestSpec).system(contains("工业设备运维"));
        verify(requestSpec).user(contains("设备温度过高"));
    }

    @Test
    void answerWithRag_noChunks_shouldReturnFallbackWithoutCallingAi() {
        when(ragRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());

        RagAnswerResult result = aiService.answerWithRag("设备手册");

        assertEquals("知识库中未找到相关内容，请先导入设备手册或运维资料。", result.getAnswer());
        assertEquals(0, result.getSources().size());
        verify(chatClient, never()).prompt();
        verify(deepSeekClient, never()).ensureAvailable();
    }

    private void stubChatClient() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
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
        return d;
    }

    private Alarm alarm() {
        Alarm a = new Alarm();
        a.setId(1L);
        a.setDeviceId(1L);
        a.setAlarmType("OVER_TEMP");
        a.setAlarmLevel(2);
        a.setAlarmMessage("温度超过 80°C");
        a.setStatus(0);
        a.setTriggeredAt(LocalDateTime.of(2026, 8, 28, 10, 0));
        return a;
    }

    private DeviceData deviceData() {
        DeviceData d = new DeviceData();
        d.setId(1L);
        d.setDeviceId(1L);
        d.setDataType("TEMPERATURE");
        d.setDataValue(new BigDecimal("82.5"));
        d.setUnit("°C");
        d.setRecordedAt(LocalDateTime.of(2026, 8, 28, 10, 0));
        return d;
    }

    private DeepSeekChatResponse responseWith(String content) {
        DeepSeekChatResponse response = new DeepSeekChatResponse();
        response.setModel("deepseek-chat");
        DeepSeekMessage message = new DeepSeekMessage("assistant", content);
        DeepSeekChoice choice = new DeepSeekChoice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");
        response.setChoices(List.of(choice));
        DeepSeekUsage usage = new DeepSeekUsage();
        usage.setPromptTokens(12);
        usage.setCompletionTokens(5);
        usage.setTotalTokens(17);
        response.setUsage(usage);
        return response;
    }

    private KnowledgeChunk knowledgeChunk() {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setSource("device-manual");
        chunk.setChunkIndex(0);
        chunk.setChunkCount(1);
        chunk.setContent("设备温度过高时请检查散热片并重新校准传感器。");
        chunk.setScore(0.95);
        return chunk;
    }
}
