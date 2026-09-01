package dev.reboot.mcp;

import dev.reboot.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * McpController 审计契约测试（DG-002，Day 83）。
 *
 * <p>{@code POST /api/mcp/smoke} 属运维诊断入口，必须携带 {@code @OperationLog}
 * 并以 {@code MCP_SMOKE / MCP} 写入审计（Flyway V12 扩展 CHECK）。</p>
 */
class McpControllerAuditTest {

    @Test
    void smokeEndpoint_shouldBeAnnotatedAsMcpSmokeAudit() throws Exception {
        Method smoke = McpController.class.getMethod("smoke");

        assertNotNull(smoke.getAnnotation(PostMapping.class), "smoke 应为 POST 端点");
        OperationLog operationLog = smoke.getAnnotation(OperationLog.class);
        assertNotNull(operationLog, "POST /api/mcp/smoke 应携带 @OperationLog");
        assertEquals("MCP_SMOKE", operationLog.operationType());
        assertEquals("MCP", operationLog.targetType());
        assertNotNull(operationLog.description());
    }
}
