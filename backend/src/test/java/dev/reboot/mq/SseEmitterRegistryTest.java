package dev.reboot.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SseEmitterRegistry 单元测试（Day 85 Phase 4，ADR 0031 §5.2）。
 *
 * <h3>覆盖路径</h3>
 * <ul>
 *   <li>register —— 创建会话、返回 emitter、size+1、get 可取回；</li>
 *   <li>register ADMIN —— 空 siteIds 语义保留；null siteIds 归一化为空；</li>
 *   <li>register 同 userId 重复 —— 旧会话被替换（覆盖语义，防泄漏）；</li>
 *   <li>findBySiteId —— 返回 ADMIN + 站点匹配会话；</li>
 *   <li>findAdmins —— 仅返回 siteIds 为空的 ADMIN 会话；</li>
 *   <li>findAll —— 返回快照副本，修改快照不影响 registry；</li>
 *   <li>remove —— 移除会话；不存在 userId 不抛；</li>
 *   <li>shutdown —— 清空全部会话；空 registry 不抛。</li>
 * </ul>
 *
 * <p><b>不测</b>：emitter.onCompletion/onTimeout/onError 回调触发 auto-remove
 * —— SseEmitter 在无 HTTP handler 的单测环境下 complete() 不会触发回调，
 * 该行为由 Spring MVC 框架保证，回调注册代码本身是单行 lambda，无单独测试价值。</p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 4)
 */
class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void register_shouldCreateSessionAndReturnEmitter() {
        SseEmitter emitter = registry.register(1L, List.of(10L, 20L));

        assertNotNull(emitter);
        assertEquals(1, registry.size());
        SseEmitterSession session = registry.get(1L);
        assertNotNull(session);
        assertEquals(1L, session.getUserId());
        assertEquals(List.of(10L, 20L), session.getSiteIds());
        assertSame(emitter, session.getEmitter());
    }

    @Test
    void register_adminUser_emptySiteIds() {
        registry.register(1L, List.of());

        SseEmitterSession session = registry.get(1L);
        assertNotNull(session);
        assertTrue(session.getSiteIds().isEmpty());
    }

    @Test
    void register_nullSiteIds_normalizedToEmpty() {
        registry.register(1L, null);

        SseEmitterSession session = registry.get(1L);
        assertNotNull(session);
        assertTrue(session.getSiteIds().isEmpty());
    }

    @Test
    void register_sameUserIdTwice_shouldReplaceOldSession() {
        SseEmitter oldEmitter = registry.register(1L, List.of(10L));
        assertEquals(1, registry.size());

        SseEmitter newEmitter = registry.register(1L, List.of(20L));

        // 仍只有 1 个会话（覆盖语义）
        assertEquals(1, registry.size());
        SseEmitterSession session = registry.get(1L);
        assertSame(newEmitter, session.getEmitter());
        assertNotSame(oldEmitter, newEmitter);
        assertEquals(List.of(20L), session.getSiteIds());
    }

    @Test
    void findBySiteId_shouldReturnAdminAndMatchingSessions() {
        registry.register(1L, List.of());          // ADMIN 全站点
        registry.register(2L, List.of(10L, 20L));   // 含 siteId=10
        registry.register(3L, List.of(30L));         // 不含 siteId=10

        List<SseEmitterSession> result = registry.findBySiteId(10L);

        assertEquals(2, result.size());
        List<Long> userIds = result.stream().map(SseEmitterSession::getUserId).toList();
        assertTrue(userIds.contains(1L)); // ADMIN
        assertTrue(userIds.contains(2L)); // siteId=10 命中
    }

    @Test
    void findBySiteId_noMatch_returnsEmptyList() {
        registry.register(1L, List.of(30L));

        List<SseEmitterSession> result = registry.findBySiteId(10L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findAdmins_shouldReturnOnlyAdminSessions() {
        registry.register(1L, List.of());      // ADMIN
        registry.register(2L, List.of(10L));    // 非 ADMIN
        registry.register(3L, List.of());      // ADMIN

        List<SseEmitterSession> result = registry.findAdmins();

        assertEquals(2, result.size());
        List<Long> userIds = result.stream().map(SseEmitterSession::getUserId).toList();
        assertTrue(userIds.contains(1L));
        assertTrue(userIds.contains(3L));
    }

    @Test
    void findAdmins_noAdmins_returnsEmptyList() {
        registry.register(1L, List.of(10L));

        List<SseEmitterSession> result = registry.findAdmins();

        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_shouldReturnSnapshotCopy() {
        registry.register(1L, List.of(10L));
        registry.register(2L, List.of(20L));

        List<SseEmitterSession> snapshot = registry.findAll();

        assertEquals(2, snapshot.size());
        // 修改快照不应影响 registry 内部状态
        snapshot.clear();
        assertEquals(2, registry.size());
    }

    @Test
    void findAll_emptyRegistry_returnsEmptyList() {
        List<SseEmitterSession> snapshot = registry.findAll();
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void remove_shouldRemoveSession() {
        registry.register(1L, List.of(10L));
        assertEquals(1, registry.size());

        registry.remove(1L);

        assertEquals(0, registry.size());
        assertNull(registry.get(1L));
    }

    @Test
    void remove_nonExistentUserId_shouldNotThrow() {
        assertDoesNotThrow(() -> registry.remove(999L));
        assertEquals(0, registry.size());
    }

    @Test
    void size_emptyRegistry_returnsZero() {
        assertEquals(0, registry.size());
    }

    @Test
    void shutdown_shouldClearAllSessions() {
        registry.register(1L, List.of(10L));
        registry.register(2L, List.of(20L));
        assertEquals(2, registry.size());

        registry.shutdown();

        assertEquals(0, registry.size());
    }

    @Test
    void shutdown_emptyRegistry_shouldNotThrow() {
        assertDoesNotThrow(() -> registry.shutdown());
        assertEquals(0, registry.size());
    }
}
