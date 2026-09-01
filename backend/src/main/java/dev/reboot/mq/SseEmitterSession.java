package dev.reboot.mq;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

/**
 * SSE 会话值对象 — 绑定 userId + 可访问 siteIds + emitter（Day 85 Phase 4，ADR 0031 §5.2）。
 *
 * <p>由 {@link SseEmitterRegistry#register} 在 emitter 建连时创建，
 * {@link InspectionPushGateway#push} 路由时按 {@link #siteIds} 交集匹配。</p>
 *
 * <h3>siteIds 语义（ADR 0031 §5.4）</h3>
 * <ul>
 *   <li>空 List —— ADMIN 全站点，可接收所有日报推送；</li>
 *   <li>非空 List —— 用户可访问的站点集合，仅接收交集非空的日报。</li>
 * </ul>
 *
 * <p><b>不可变</b>：建连后 siteIds 不再修改，避免路由期并发污染。
 * emitter complete/timeout/error 时由 Registry 统一移除。</p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 4)
 */
public class SseEmitterSession {

    private final long userId;
    /** 可访问站点集合；空 List = ADMIN 全站点（ADR 0031 §5.4）。 */
    private final List<Long> siteIds;
    private final SseEmitter emitter;
    private final Instant createdAt;

    public SseEmitterSession(long userId, List<Long> siteIds, SseEmitter emitter) {
        this.userId = userId;
        this.siteIds = siteIds == null ? List.of() : List.copyOf(siteIds);
        this.emitter = emitter;
        this.createdAt = Instant.now();
    }

    public long getUserId() { return userId; }
    public List<Long> getSiteIds() { return siteIds; }
    public SseEmitter getEmitter() { return emitter; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * 判断本会话是否能接收指定 siteIds 范围的日报（ADR 0031 §5.3 路由匹配）。
     *
     * <ul>
     *   <li>本会话 siteIds 为空（ADMIN）→ 接收所有；</li>
     *   <li>日报 siteIds 为空（ADMIN 全站点巡检）→ 接收；</li>
     *   <li>两者都非空 → 交集非空即接收。</li>
     * </ul>
     */
    public boolean canReceive(List<Long> messageSiteIds) {
        if (this.siteIds.isEmpty()) {
            return true; // ADMIN 全站点
        }
        if (messageSiteIds == null || messageSiteIds.isEmpty()) {
            return true; // 日报全站点语义
        }
        return messageSiteIds.stream().anyMatch(this.siteIds::contains);
    }
}
