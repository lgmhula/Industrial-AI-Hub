package dev.reboot.aop;

import dev.reboot.annotation.OperationLog;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.ai.AiDeviceStatusResult;
import dev.reboot.dto.ai.AiInspectionReportResult;
import dev.reboot.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperationLogAspect 单元测试 —— {ret} 返回值占位符（FUNCTION_CALL 审计记录轮次/调用数）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock private OperationLogMapper operationLogMapper;

    private OperationLogAspect aspect;

    /** 测试接口：deviceStatus 方法携带 FUNCTION_CALL 注解与 {ret} 占位符。 */
    interface TestOps {
        @OperationLog(operationType = "FUNCTION_CALL", targetType = "AI",
                description = "AI 设备状态问答（工具调用） {ret}")
        AiDeviceStatusResult deviceStatus();

        @OperationLog(operationType = "MCP_SMOKE", targetType = "MCP",
                description = "MCP 客户端冒烟（握手 + 工具清单 + 只读探针） {ret}")
        String mcpSmoke();

        /** 模拟 Controller 生产签名：返回 ApiResponse 包装的巡检日报，见 Week12 Exit Gate P1-1。 */
        @OperationLog(operationType = "INSPECTION", targetType = "MCP",
                description = "AI 设备巡检日报（MCP 工具调用） {ret}")
        ApiResponse<AiInspectionReportResult> inspectionReport();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        aspect = new OperationLogAspect(operationLogMapper);
    }

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void around_withRetPlaceholder_shouldRecordRoundsAndCallCountInDescription() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(7L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = TestOps.class.getMethod("deviceStatus");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(deviceStatusResult());

        Object returned = aspect.around(joinPoint);

        assertEquals(deviceStatusResult().toString(), returned.toString());
        ArgumentCaptor<dev.reboot.entity.OperationLog> captor =
                ArgumentCaptor.forClass(dev.reboot.entity.OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        dev.reboot.entity.OperationLog log = captor.getValue();
        assertEquals("FUNCTION_CALL", log.getOperationType());
        assertEquals("AI", log.getTargetType());
        assertEquals(7L, log.getUserId());
        assertTrue(log.getDescription().contains("rounds=2"), "description 应含轮次: " + log.getDescription());
        assertTrue(log.getDescription().contains("calls=3"), "description 应含调用数: " + log.getDescription());
        assertTrue(log.getDescription().contains("realtime=true"), "description 应含实时数据标记: " + log.getDescription());
    }

    @Test
    void around_whenMethodThrows_shouldPrefixFailureAndRecordExceptionMessage() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = TestOps.class.getMethod("deviceStatus");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        try {
            aspect.around(joinPoint);
        } catch (IllegalStateException expected) {
            // expected
        }

        ArgumentCaptor<dev.reboot.entity.OperationLog> captor =
                ArgumentCaptor.forClass(dev.reboot.entity.OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        String desc = captor.getValue().getDescription();
        assertTrue(desc.startsWith("[失败] AI 设备状态问答（工具调用） "), "应以 [失败] 开头: " + desc);
        assertTrue(desc.contains("boom"), "失败时 {ret} 应替换为异常消息: " + desc);
        assertTrue(!desc.endsWith(" null"), "不应以 null 结尾: " + desc);
    }

    @Test
    void around_withMcpSmoke_shouldRecordMcpOperationAndTargetType() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(2L);
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = TestOps.class.getMethod("mcpSmoke");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("industrial-ai-hub-mcp");

        Object returned = aspect.around(joinPoint);

        assertEquals("industrial-ai-hub-mcp", returned.toString());
        ArgumentCaptor<dev.reboot.entity.OperationLog> captor =
                ArgumentCaptor.forClass(dev.reboot.entity.OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        dev.reboot.entity.OperationLog log = captor.getValue();
        assertEquals("MCP_SMOKE", log.getOperationType());
        assertEquals("MCP", log.getTargetType());
        assertEquals(2L, log.getUserId());
        assertEquals("MCP 客户端冒烟（握手 + 工具清单 + 只读探针） industrial-ai-hub-mcp",
                log.getDescription());
    }

    /**
     * 生产模式回归：Controller 返回 ApiResponse&lt;AiInspectionReportResult&gt;。
     *
     * <p>修复前 formatResult 直接对 ApiResponse 调 toString()，得到
     * {@code ApiResponse@hash}，丢失 rounds/calls/devices/alarms/truncated 摘要。
     * 此测试用真实 ApiResponse 包装断言解包逻辑，避免重蹈 Day 83 Exit Review P1-1 覆辙。</p>
     */
    @Test
    void around_withApiResponseWrappedResult_shouldUnwrapDataForDescription() throws Throwable {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = TestOps.class.getMethod("inspectionReport");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(ApiResponse.ok(inspectionReportResult()));

        Object returned = aspect.around(joinPoint);

        assertTrue(returned instanceof ApiResponse,
                "应原样返回 ApiResponse 包装: " + returned.getClass());
        ArgumentCaptor<dev.reboot.entity.OperationLog> captor =
                ArgumentCaptor.forClass(dev.reboot.entity.OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        String desc = captor.getValue().getDescription();
        assertFalse(desc.contains("ApiResponse@"),
                "description 不应出现 ApiResponse@hash: " + desc);
        assertTrue(desc.contains("rounds=6"),
                "description 应含轮次: " + desc);
        assertTrue(desc.contains("calls=66"),
                "description 应含调用数: " + desc);
        assertTrue(desc.contains("devices=20"),
                "description 应含设备数: " + desc);
        assertTrue(desc.contains("alarms=2"),
                "description 应含告警数: " + desc);
        assertTrue(desc.contains("truncated=true"),
                "description 应含截断标记: " + desc);
    }

    private AiDeviceStatusResult deviceStatusResult() {
        AiDeviceStatusResult result = new AiDeviceStatusResult();
        result.setDeviceId(1L);
        result.setAnswer("设备在线");
        result.setToolRounds(2);
        result.setToolCalls(3);
        result.setReferencedRealTime(true);
        result.setTruncated(false);
        return result;
    }

    private AiInspectionReportResult inspectionReportResult() {
        AiInspectionReportResult r = new AiInspectionReportResult();
        r.setReportDate(LocalDate.of(2026, 8, 31));
        r.setReport("# 巡检日报\n设备全部在线");
        r.setToolRounds(6);
        r.setToolCalls(66);
        r.setDeviceCount(20);
        r.setAlarmCount(2);
        r.setTruncated(true);
        return r;
    }
}
