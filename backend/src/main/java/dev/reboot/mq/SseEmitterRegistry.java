package dev.reboot.mq;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 会话注册表 — 维护 userId → {@link SseEmitterSession} 映射（Day 85 Phase 4，ADR 0031 §5.2）。
 *
 * <p>本类是 ADR 0031 六段链路中「Push Gateway」层的本地 emitter 表，
 * 单副本进程内直连（§5.3 进程内直连策略）。多副本生产部署前需切 Redis pub/sub
 * （§3.2 / §9 风险表），本类为 Day 85 单副本基线。</p>
 *
 * <h3>生命周期管理（ADR 0031 §6 emitter 泄漏行）</h3>
 * <ul>
 *   <li>timeout 30min —— {@link #EMITTER_TIMEOUT_MS}，建连时由 SseEmitter 构造器设置；</li>
 *   <li>回调自动移除 —— {@code onCompletion}/{@code onTimeout}/{@code onError}
 *       注册时挂到 emitter，触发后调 {@link #remove(long)}；</li>
 *   <li>JVM shutdown —— {@link #shutdown()} 用 {@link PreDestroy} 关闭全部 emitter，
 *       避免容器重启时残留连接。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>用 {@link ConcurrentHashMap}，register/remove/get 均无锁；
 * {@link #findBySiteId} 与 {@link #findAdmins} 返回快照副本，路由期不持有锁。</p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 4)
 */
@Component
@Profile("!test")
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** emitter 超时时间（ADR 0031 §5.2：30min）。 */
    static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<Long, SseEmitterSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册一个 SSE 会话（由 Phase 6 Controller 在 emitter 建连时调用）。
     *
     * <p>同一 userId 重复建连时<b>覆盖</b>旧会话（先关闭旧 emitter 再覆盖），
     * 避免同一用户多 emitter 泄漏。</p>
     *
     * @param userId  JWT 解析的 userId（ADR 0031 §5.5：只认建连时绑定的 userId）
     * @param siteIds 用户可访问的站点集合；空 List = ADMIN 全站点
     * @return 新创建的 SseEmitter，由 Controller 直接返回给客户端
     */
    public SseEmitter register(long userId, List<Long> siteIds) {
        // 同 userId 旧会话先关闭，避免泄漏
        SseEmitterSession old = sessions.get(userId);
        if (old != null) {
            closeQuietly(old.getEmitter(), "register 覆盖旧会话 userId=" + userId);
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        SseEmitterSession session = new SseEmitterSession(userId, siteIds, emitter);
        sessions.put(userId, session);
        // 回调自动移除（emitter complete/timeout/error 任一触发）
        emitter.onCompletion(() -> remove(userId));
        emitter.onTimeout(() -> remove(userId));
        emitter.onError(e -> remove(userId));
        log.info("SSE 会话注册 userId={} siteIds={} 当前在线={}", userId, siteIds, sessions.size());
        return emitter;
    }

    /** 获取指定 userId 的会话（路由或主动关闭用）。 */
    public SseEmitterSession get(long userId) {
        return sessions.get(userId);
    }

    /**
     * 返回能接收指定 siteId 日报的所有会话（ADR 0031 §5.3 路由匹配）。
     * <p>包含 ADMIN 全站点会话 + siteIds 包含该 siteId 的会话。</p>
     */
    public List<SseEmitterSession> findBySiteId(long siteId) {
        List<SseEmitterSession> result = new ArrayList<>();
        for (SseEmitterSession s : sessions.values()) {
            if (s.getSiteIds().isEmpty() || s.getSiteIds().contains(siteId)) {
                result.add(s);
            }
        }
        return result;
    }

    /** 返回所有 ADMIN 全站点会话（siteIds 为空）。 */
    public List<SseEmitterSession> findAdmins() {
        List<SseEmitterSession> result = new ArrayList<>();
        for (SseEmitterSession s : sessions.values()) {
            if (s.getSiteIds().isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    /** 返回所有会话快照（Push Gateway 路由用，避免持锁遍历）。 */
    public List<SseEmitterSession> findAll() {
        return new ArrayList<>(sessions.values());
    }

    /** 移除会话（emitter 回调或主动关闭时调用）。 */
    public void remove(long userId) {
        SseEmitterSession removed = sessions.remove(userId);
        if (removed != null) {
            log.info("SSE 会话移除 userId={} 当前在线={}", userId, sessions.size());
        }
    }

    /** 当前在线会话数（监控/日志用）。 */
    public int size() {
        return sessions.size();
    }

    /**
     * JVM shutdown 时关闭全部 emitter（ADR 0031 §6 emitter 泄漏行）。
     * <p>由 Spring 容器关闭时自动调用（@PreDestroy），避免容器重启残留连接。</p>
     */
    @PreDestroy
    public void shutdown() {
        int count = sessions.size();
        for (SseEmitterSession s : sessions.values()) {
            closeQuietly(s.getEmitter(), "shutdown userId=" + s.getUserId());
        }
        sessions.clear();
        log.info("SseEmitterRegistry shutdown：已关闭 {} 个会话", count);
    }

    private void closeQuietly(SseEmitter emitter, String reason) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("emitter 关闭异常（{}）：{}", reason, e.getMessage());
        }
    }
}
