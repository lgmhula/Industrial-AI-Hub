package dev.reboot.aop;

import dev.reboot.annotation.OperationLog;
import dev.reboot.dto.ai.AiDeviceStatusResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
