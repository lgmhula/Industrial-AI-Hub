package dev.reboot.controller;

import dev.reboot.exception.BusinessException;
import dev.reboot.mq.SseEmitterRegistry;
import dev.reboot.service.SiteAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InspectionPushController 单元测试（Day 85 Phase 6，ADR 0031 §5.2/§5.5）。
 *
 * <h3>覆盖路径</h3>
 * <ul>
 *   <li>ADMIN 用户 —— accessibleSiteIds 返回 null → register(userId, List.of()) → 返回 emitter；</li>
 *   <li>非 ADMIN 有站点 —— accessibleSiteIds 返回非空 List → register(userId, siteIds) → 返回 emitter；</li>
 *   <li>非 ADMIN 无站点 —— accessibleSiteIds 返回空 List → 抛 BusinessException(403)，
 *       不调 register（P0 安全防护：空 siteIds 在 SseEmitterSession 中被当作 ADMIN 全站点语义）。</li>
 * </ul>
 *
 * <p>Mock 策略：{@link SseEmitterRegistry} + {@link SiteAccessService} + {@link HttpServletRequest}
 * 均 mock 化；{@code registry.register} 返回真实 {@code new SseEmitter(0L)} 以便 assertSame。</p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 6)
 */
@ExtendWith(MockitoExtension.class)
class InspectionPushControllerTest {

    @Mock private SseEmitterRegistry registry;
    @Mock private SiteAccessService siteAccessService;
    @Mock private HttpServletRequest request;

    private InspectionPushController controller;

    @BeforeEach
    void setUp() {
        controller = new InspectionPushController(registry, siteAccessService);
    }

    @Test
    void subscribe_adminUser_shouldRegisterWithEmptySiteIds() {
        when(request.getAttribute("userId")).thenReturn(1L);
        when(siteAccessService.accessibleSiteIds(1L)).thenReturn(null); // ADMIN = null
        SseEmitter mockEmitter = new SseEmitter(0L);
        when(registry.register(eq(1L), eq(List.of()))).thenReturn(mockEmitter);

        SseEmitter result = controller.subscribe(request);

        assertSame(mockEmitter, result);
        // ADMIN 传 List.of()（SseEmitterSession 空 siteIds = ADMIN 全站点语义）
        verify(registry).register(1L, List.of());
    }

    @Test
    void subscribe_nonAdminWithSites_shouldRegisterWithActualSiteIds() {
        when(request.getAttribute("userId")).thenReturn(2L);
        List<Long> siteIds = List.of(10L, 20L);
        when(siteAccessService.accessibleSiteIds(2L)).thenReturn(siteIds);
        SseEmitter mockEmitter = new SseEmitter(0L);
        when(registry.register(eq(2L), eq(siteIds))).thenReturn(mockEmitter);

        SseEmitter result = controller.subscribe(request);

        assertSame(mockEmitter, result);
        // 非 ADMIN 传实际 siteIds（canReceive 按交集匹配）
        verify(registry).register(2L, siteIds);
    }

    @Test
    void subscribe_nonAdminNoSites_shouldThrow403AndNotRegister() {
        when(request.getAttribute("userId")).thenReturn(3L);
        when(siteAccessService.accessibleSiteIds(3L)).thenReturn(List.of()); // 无站点

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.subscribe(request));

        // P0 安全防护：空 siteIds 会被 SseEmitterSession 当作 ADMIN 全站点 → 拒绝
        assertTrue(ex.getMessage().contains("无可访问站点"));
        // 不应调用 register（避免无站点用户建立 emitter 后收到所有日报）
        verify(registry, never()).register(any(Long.class), any());
    }
}
