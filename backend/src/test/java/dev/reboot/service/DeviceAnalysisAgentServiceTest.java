package dev.reboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.agent.AgentRunResult;
import dev.reboot.agent.ToolCallingAgent;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.dto.ai.AiDeviceStatusRequest;
import dev.reboot.dto.ai.AiDeviceStatusResult;
import dev.reboot.dto.ai.AiToolCallTrace;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.tool.DeviceAiTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceAnalysisAgentService 单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ExtendWith(MockitoExtension.class)
class DeviceAnalysisAgentServiceTest {

    @Mock private DeepSeekClient deepSeekClient;
    @Mock private DeviceMapper deviceMapper;
    @Mock private AlarmMapper alarmMapper;
    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private SiteAccessService siteAccessService;
    @Mock private ToolCallingAgent toolCallingAgent;

    private DeviceAnalysisAgentService service;

    @BeforeEach
    void setUp() {
        DeviceAiTools tools = new DeviceAiTools(deviceMapper, alarmMapper, deviceDataMapper,
                siteAccessService, new ObjectMapper());
        service = new DeviceAnalysisAgentService(deepSeekClient, deviceMapper, siteAccessService,
                tools, toolCallingAgent);
    }

    @Test
    void analyze_shouldDelegateToAgentAndMapResult() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(toolCallingAgent.run(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(new AgentRunResult("设备运行正常，建议定期巡检。", 2, 3, true, false,
                        List.of(new AiToolCallTrace("get_device_basic", true))));

        AiDeviceStatusResult result = service.analyze(request(1L, "请分析设备运行状态"), 7L);

        assertEquals("设备运行正常，建议定期巡检。", result.getAnswer());
        assertEquals(2, result.getToolRounds());
        assertEquals(3, result.getToolCalls());
        assertTrue(result.isReferencedRealTime());
        assertEquals(1, result.getToolTrace().size());
        verify(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
        verify(deepSeekClient).ensureAvailable();
        verify(toolCallingAgent).run(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void analyze_noSiteAccess_shouldFailBeforeAgentRun() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);

        assertThrows(BusinessException.class,
                () -> service.analyze(request(1L, "请分析设备运行状态"), 7L));
        verify(toolCallingAgent, never()).run(anyString(), anyString(), any(), any(), anyInt());
        verify(deepSeekClient, never()).ensureAvailable();
    }

    private AiDeviceStatusRequest request(Long deviceId, String question) {
        AiDeviceStatusRequest request = new AiDeviceStatusRequest();
        request.setDeviceId(deviceId);
        request.setQuestion(question);
        return request;
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
}
