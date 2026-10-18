package dev.reboot.mq;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 会话注册表 — 维护 userId → {@link SseEmitterSession} 映射（Day 85 Phase 4，ADR 0031 §5.2）。
 *
 * <p>本类是各后端实例持有的本地 emitter 表。RabbitMQ Consumer 取得幂等键后通过
 * Redis Pub/Sub 广播日报，各实例的 {@link InspectionPushGateway} 再向本机 emitter
 * 推送，从而覆盖多实例连接。该方案降低单机连接绑定风险，但不表示完整的生产级
 * 高可用承诺。</p>
 *
 * <h3>生命周期管理（ADR 0031 §6 emitter 泄漏行）</h3>
 * <ul>
 *   <li>timeout 30min —— {@link #EMITTER_TIMEOUT_MS}，建连时由 SseEmitter 构造器设置；</li>
 *   <li>回调自动移除 —— {@code onCompletion}/{@code onTimeout}/{@code onError}
 *       注册时挂到 emitter，触发后按 emitter 实例条件移除；</li>
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

    /** 等待 Spring MVC 进入异步响应模式后再提交首帧。 */
    static final long FIRST_FRAME_DELAY_MS = 100L;

    /** 心跳间隔：浏览器可及时发现半开连接并触发 EventSource 重连。 */
    static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final ConcurrentHashMap<Long, SseEmitterSession> sessions = new ConcurrentHashMap<>();
    private final TaskScheduler firstFrameScheduler;
    private final AtomicBoolean heartbeatScheduled = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> heartbeatFuture;

    public SseEmitterRegistry(
            @Qualifier("sseFirstFrameScheduler") TaskScheduler firstFrameScheduler) {
        this.firstFrameScheduler = firstFrameScheduler;
    }

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
        SseEmitter emitter = createEmitter();
        SseEmitterSession session = new SseEmitterSession(userId, siteIds, emitter);
        SseEmitterSession old = sessions.put(userId, session);
        // 回调必须匹配 emitter 实例，避免旧连接迟到回调误删刚建立的新会话。
        emitter.onCompletion(() -> removeIfCurrent(userId, emitter));
        emitter.onTimeout(() -> removeIfCurrent(userId, emitter));
        emitter.onError(e -> removeIfCurrent(userId, emitter));
        if (old != null) {
            closeQuietly(old.getEmitter(), "register 覆盖旧会话 userId=" + userId);
        }
        ensureHeartbeatScheduled();
        scheduleConnectedComment(userId, emitter);
        log.info("SSE 会话注册 userId={} siteIds={} 当前在线={}", userId, siteIds, sessions.size());
        return emitter;
    }

    /**
     * 首次建连时启动进程级心跳任务，后续连接复用同一个调度任务。
     */
    private void ensureHeartbeatScheduled() {
        if (!heartbeatScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            heartbeatFuture = firstFrameScheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    Instant.now().plusMillis(HEARTBEAT_INTERVAL_MS),
                    Duration.ofMillis(HEARTBEAT_INTERVAL_MS));
        } catch (RuntimeException e) {
            heartbeatScheduled.set(false);
            log.warn("SSE 心跳调度失败: {}", e.getMessage());
        }
    }

    /**
     * 向所有本机会话发送 SSE comment 心跳。发送失败即清理失效连接。
     */
    void sendHeartbeat() {
        for (SseEmitterSession session : sessions.values()) {
            SseEmitter emitter = session.getEmitter();
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
            } catch (IOException | IllegalStateException e) {
                // 响应通道已损坏。仅移除本地会话，不再 complete() 触发一次注定失败的
                // async dispatch，否则 Spring 会把客户端断线升级为 GlobalExceptionHandler 的 ERROR。
                removeIfCurrent(session.getUserId(), emitter);
                log.debug("SSE 心跳发送失败，已移除会话 userId={}: {}",
                        session.getUserId(), e.getMessage());
            }
        }
    }

    /**
     * 在 Controller 返回 emitter、Spring MVC 启动异步处理后提交 SSE 响应头。
     */
    private void scheduleConnectedComment(long userId, SseEmitter emitter) {
        try {
            firstFrameScheduler.schedule(
                    () -> sendConnectedComment(userId, emitter),
                    Instant.now().plusMillis(FIRST_FRAME_DELAY_MS));
        } catch (RuntimeException e) {
            if (removeIfCurrent(userId, emitter)) {
                closeQuietly(emitter, "首帧调度失败 userId=" + userId);
            }
            log.warn("SSE 首帧调度失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 发送连接确认 comment。失败只清理当前 emitter，避免旧连接迟到任务误删新连接。
     */
    private void sendConnectedComment(long userId, SseEmitter emitter) {
        SseEmitterSession current = sessions.get(userId);
        if (current == null || current.getEmitter() != emitter) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().comment("connected"));
            }
        } catch (IOException | IllegalStateException e) {
            // 同心跳失败：传输已不可用，注册表移除即完成服务端清理。
            removeIfCurrent(userId, emitter);
            log.debug("SSE 首帧发送失败，已移除会话 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 创建连接 emitter。默认使用生产超时配置；集成测试可覆盖此方法记录事件，
     * 避免构造依赖真实 Web 异步上下文的 Servlet 响应。
     */
    protected SseEmitter createEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
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

    /**
     * 仅当 registry 当前仍保存该 emitter 时移除会话。
     *
     * <p>用于连接回调、首帧失败和推送失败等可能基于旧快照执行的路径，防止
     * 同一用户重连后，旧 emitter 的迟到事件误删新连接。</p>
     *
     * @return true = 当前会话已被移除；false = 已被新连接替换或会话不存在
     */
    public boolean removeIfCurrent(long userId, SseEmitter emitter) {
        SseEmitterSession session = sessions.get(userId);
        if (session == null || session.getEmitter() != emitter) {
            return false;
        }
        boolean removed = sessions.remove(userId, session);
        if (removed) {
            log.info("SSE 会话移除 userId={} 当前在线={}", userId, sessions.size());
        }
        return removed;
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
        ScheduledFuture<?> heartbeat = heartbeatFuture;
        if (heartbeat != null) {
            heartbeat.cancel(false);
            heartbeatFuture = null;
        }
        heartbeatScheduled.set(false);
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
