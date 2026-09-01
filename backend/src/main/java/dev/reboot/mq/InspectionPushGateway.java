package dev.reboot.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * AI 巡检日报推送网关 — 按 siteIds 路由到本地 SSE emitter（Day 85 Phase 4，ADR 0031 §5.3）。
 *
 * <p>本类是 ADR 0031 六段链路中「Push Gateway」层的路由器，承接
 * {@link InspectionReportConsumer} 的调用，遍历 {@link SseEmitterRegistry}
 * 中本地 emitter，按 {@link SseEmitterSession#canReceive} 匹配后
 * {@code emitter.send()} 推送日报。</p>
 *
 * <h3>路由策略（ADR 0031 §5.3 单副本进程内直连）</h3>
 * <pre>
 * Consumer.handleReport(message)
 *   ↓ gateway.push(message)
 * PushGateway.push()
 *   ├─ 遍历 registry.findAll() 快照
 *   ├─ session.canReceive(message.siteIds) 命中 → emitter.send()
 *   └─ emitter.send 抛 IOException → registry.remove(userId) 避免泄漏
 * </pre>
 *
 * <h3>失败策略（ADR 0031 §6 各异常行）</h3>
 * <ul>
 *   <li>emitter.send IOException —— 连接已断，{@code registry.remove} 后继续推送其他用户，
 *       不影响 Consumer 主流程；</li>
 *   <li>无在线 emitter —— 日志 INFO，不报错（日报已生成，无人订阅属正常）。</li>
 * </ul>
 *
 * <h3>边界约束（ADR 0031 §5.5）</h3>
 * <p>本类<b>不信任</b> Consumer 传入的 userId，只认 emitter 建连时
 * 由 {@link SseEmitterRegistry#register} 绑定的 userId + siteIds；
 * {@link InspectionReportMessage#getTriggeredByUserId} 仅审计用，不参与路由。</p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 4)
 */
@Component
@Profile("!test")
public class InspectionPushGateway {

    private static final Logger log = LoggerFactory.getLogger(InspectionPushGateway.class);

    private final SseEmitterRegistry registry;

    public InspectionPushGateway(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 推送巡检日报到所有匹配的在线 emitter（ADR 0031 §5.3 路由）。
     *
     * <p>由 {@link InspectionReportConsumer#handleReport} 在幂等检查通过后调用。
     * 单个 emitter 推送失败不阻塞其他用户，整体失败由 Consumer catch 后 nack→DLQ。</p>
     *
     * @param message 巡检日报消息（不可为 null）
     */
    public void push(InspectionReportMessage message) {
        List<SseEmitterSession> snapshot = registry.findAll();
        if (snapshot.isEmpty()) {
            log.info("巡检日报无人订阅，跳过推送: {}", message);
            return;
        }
        int sent = 0;
        int failed = 0;
        for (SseEmitterSession session : snapshot) {
            if (!session.canReceive(message.getSiteIds())) {
                continue; // 站点隔离：不匹配的会话跳过
            }
            if (sendSafely(session, message)) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("巡检日报推送完成 sent={} failed={} total={} message={}",
                sent, failed, snapshot.size(), message);
    }

    /**
     * 向单个会话推送日报，捕获 IOException 后移除失效 emitter。
     *
     * @return true = 推送成功；false = 连接已断已移除
     */
    private boolean sendSafely(SseEmitterSession session, InspectionReportMessage message) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name("inspection-report")
                    .data(message)
                    .id(String.valueOf(System.currentTimeMillis()));
            session.getEmitter().send(builder);
            return true;
        } catch (IOException e) {
            log.warn("SSE 推送失败，移除失效会话 userId={}: {}",
                    session.getUserId(), e.getMessage());
            registry.remove(session.getUserId());
            return false;
        } catch (IllegalStateException e) {
            // emitter 已 complete/timeout 后再 send 也会抛 IllegalStateException
            log.warn("SSE 推送失败（emitter 已关闭）userId={}: {}",
                    session.getUserId(), e.getMessage());
            registry.remove(session.getUserId());
            return false;
        }
    }
}
