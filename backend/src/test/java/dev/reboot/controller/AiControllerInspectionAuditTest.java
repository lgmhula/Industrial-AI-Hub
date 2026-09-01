package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.enums.RoleEnum;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AI 巡检日报端点审计契约测试（Day 83，ADR 0030）。
 *
 * <p>巡检入口为 ADMIN 级 JWT 端点，内部经 MCP 客户端工具全量读取设备，
 * 必须携带 {@code @OperationLog(INSPECTION/MCP)}（Flyway V13 扩展 CHECK）。</p>
 */
class AiControllerInspectionAuditTest {

    @Test
    void inspectionReportEndpoint_shouldBeAdminOnlyAndAudited() throws Exception {
        Method method = AiController.class.getMethod("inspectionReport");

        assertNotNull(method.getAnnotation(PostMapping.class), "巡检日报应为 POST 端点");
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        assertNotNull(requireRole, "巡检日报端点必须声明 RBAC 权限");
        assertEquals(1, requireRole.value().length);
        assertEquals(RoleEnum.ADMIN, requireRole.value()[0]);

        OperationLog operationLog = method.getAnnotation(OperationLog.class);
        assertNotNull(operationLog, "巡检日报端点应携带 @OperationLog");
        assertEquals("INSPECTION", operationLog.operationType());
        assertEquals("MCP", operationLog.targetType());
        assertNotNull(operationLog.description());
    }
}
