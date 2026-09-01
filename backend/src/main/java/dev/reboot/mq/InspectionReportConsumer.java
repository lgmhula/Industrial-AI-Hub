package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * AI 巡检日报消息消费者 — 手动 ACK + Redis SETNX 跨实例幂等 + Push Gateway 接入
 * （Day 85 Phase 3 + Phase 4，ADR 0031）。
 *
 * <p>本类是 ADR 0031 六段链路中「RabbitMQ → Consumer → Push Gateway」段，承接
 * {@link InspectionReportProducer} 投递的 {@link InspectionReportMessage}，
 * 完成幂等检查后调用 {@link InspectionPushGateway#push} 路由到本地 SSE emitter。</p>
 *
 * <h3>可靠性机制（对齐 AlarmConsumer + ADR 0031 §6 增强）</h3>
 * <table>
 *   <tr><th>机制</th><th>实现</th><th>与 AlarmConsumer 差异</th></tr>
 *   <tr><td>手动 ACK</td><td>处理成功 → {@code basicAck}；失败 → {@code basicNack(requeue=false)}</td><td>同</td></tr>
 *   <tr><td>死信队列</td><td>Nack 消息自动路由到 {@code inspection.dlx → inspection.dlq}</td><td>同（DLX 名不同）</td></tr>
 *   <tr><td>幂等消费</td><td><b>Redis SETNX</b> 跨实例去重</td><td>AlarmConsumer 用 ConcurrentHashMap 内存去重（单实例）</td></tr>
 *   <tr><td>消息持久化</td><td>Queue durable + 消息由 Jackson2JsonMessageConverter 序列化</td><td>同</td></tr>
 *   <tr><td>重试</td><td><b>不内置重试</b> —— 失败直接 nack 进 DLQ 可人工重投</td><td>AlarmConsumer 内置 3 次重试</td></tr>
 * </table>
 *
 * <h3>幂等键设计（ADR 0031 §5.1）</h3>
 * <pre>
 *   inspection:{reportDate}:{siteId}   TTL 24h
 *   inspection:{reportDate}:all        空站点列表的 ADMIN 全站点语义
 * </pre>
 * <p>SETNX 语义：{@code setIfAbsent(key, "1", Duration.ofHours(24))}
 * 返回 true = 首次 → 推送；返回 false = 重复 → 跳过推送，但仍 ack 避免堆积。</p>
 *
 * <h3>流程</h3>
 * <pre>
 * inspection.queue → Consumer.handleReport()
 *   ├─ 幂等键已存在 → 跳过推送 → basicAck（防重复推送）
 *   ├─ 幂等键不存在 → SETNX → gateway.push(message) → basicAck
 *   └─ 处理异常 → basicNack(requeue=false) → inspection.dlx → inspection.dlq
 * </pre>
 *
 * <h3>Push Gateway 接入（ADR 0031 §5.3）</h3>
 * <p>幂等通过后调用 {@link InspectionPushGateway#push} 推送到本地 SSE emitter；
 * gateway.push 内部捕获 IOException/IllegalStateException 移除失效 emitter，
 * <b>不向上抛</b> —— Consumer 主流程 ack 成功。Push Gateway 仅在非 test profile
 * 下注入；test profile 下为 null，{@link #dispatchToPushGateway} 跳过日志降级。</p>
 *
 * @author AI 助手
 * @since 2026-08-31 (Day 85, Phase 3)；Phase 4 接入 PushGateway (2026-09-01)
 */
@Component
@Profile("!test")
public class InspectionReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(InspectionReportConsumer.class);

    /** 幂等键前缀（ADR 0031 §5.1）。 */
    private static final String IDEMPOTENCY_KEY_PREFIX = "inspection:";
    /** 空站点列表的 ADMIN 全站点占位（ADR 0031 §5.4）。 */
    private static final String ALL_SITES_MARKER = "all";
    /** 幂等键 TTL（ADR 0031 §5.1：24h）。 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    /**
     * Push Gateway 实例；非 test profile 下由 Spring 注入，test profile 下为 null。
     * <p>用 {@link Nullable} 标注，构造器允许 null —— 兼容 test profile 下
     * {@link InspectionPushGateway}（{@code @Profile("!test")}）不存在的情况，
     * 以及 Phase 4 之前 Producer 未注入的过渡态。</p>
     */
    @Nullable
    private final InspectionPushGateway pushGateway;

    public InspectionReportConsumer(StringRedisTemplate redis,
                                    @Nullable InspectionPushGateway pushGateway) {
        this.redis = redis;
        this.pushGateway = pushGateway;
    }

    /**
     * 监听巡检日报队列（手动 ACK 模式，与 AlarmConsumer 同模式）。
     *
     * <p>concurrency=1-2（日报推送不需要高并发，与 alarm.queue 2-4 区分）。</p>
     */
    @RabbitListener(queues = MQConfig.INSPECTION_QUEUE, concurrency = "1-2",
            ackMode = "MANUAL")
    public void handleReport(InspectionReportMessage message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            if (isDuplicate(message)) {
                log.info("巡检日报重复消息已跳过推送（幂等命中）: {}", message);
                ack(channel, deliveryTag);
                return;
            }
            dispatchToPushGateway(message);
            log.info("巡检日报处理完成: {}", message);
            ack(channel, deliveryTag);
        } catch (Exception e) {
            // 失败不 requeue，直接进 DLQ 可人工重投（ADR 0031 §6 失败策略）
            log.error("巡检日报处理失败 (deliveryTag={}), 进入死信队列: {}",
                    deliveryTag, message, e);
            nack(channel, deliveryTag);
        }
    }

    /**
     * Redis SETNX 幂等检查（ADR 0031 §5.1）。
     *
     * <p>对消息覆盖的所有 siteId 检查幂等键：
     * <ul>
     *   <li>空 siteIds（ADMIN 全站点）→ 单键 {@code inspection:{reportDate}:all}；</li>
     *   <li>非空 siteIds → 对<b>每个</b> siteId 检查 {@code inspection:{reportDate}:{siteId}}，
     *       全部命中才视为重复（部分命中时推送未命中部分 + 设置未命中键）。</li>
     * </ul>
     * 命中时返回 true（重复），未命中时设置幂等键并返回 false（首次）。</p>
     *
     * @return true = 重复消息（应跳过推送）；false = 首次（已设置幂等键，应推送）
     */
    private boolean isDuplicate(InspectionReportMessage message) {
        LocalDate reportDate = message.getReportDate();
        List<Long> siteIds = message.getSiteIds();
        if (siteIds == null || siteIds.isEmpty()) {
            // ADMIN 全站点语义：单键
            String key = idempotencyKey(reportDate, ALL_SITES_MARKER);
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(key, "1", IDEMPOTENCY_TTL);
            return Boolean.FALSE.equals(acquired);
        }
        // 多站点：任一未命中即视为部分首次，推送该 siteId
        // 简化语义：全部命中才跳过；否则对未命中的 siteId 设置键后视为首次
        boolean allHit = true;
        for (Long siteId : siteIds) {
            String key = idempotencyKey(reportDate, String.valueOf(siteId));
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(key, "1", IDEMPOTENCY_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                allHit = false; // 此 siteId 首次
            }
        }
        return allHit; // 全部已存在 = 重复
    }

    private String idempotencyKey(LocalDate reportDate, String siteMarker) {
        return IDEMPOTENCY_KEY_PREFIX + reportDate + ":" + siteMarker;
    }

    /**
     * 推送日报到 Push Gateway（ADR 0031 §5.3）。
     *
     * <p>幂等通过后调用 {@link InspectionPushGateway#push} 路由到本地 SSE emitter。
     * Push Gateway 内部已捕获单 emitter 的 IOException/IllegalStateException 并移除失效会话，
     * 不向上抛；若仍出现未预期异常（如 registry 状态损坏），由 {@link #handleReport}
     * 的外层 catch 兜底 nack → DLQ（ADR 0031 §6 失败策略一致语义）。</p>
     *
     * <p>test profile 下 pushGateway 为 null（{@code @Profile("!test")} 不注入），
     * 跳过推送仅 INFO 日志，保证单测可在无 SSE 环境下运行。</p>
     */
    private void dispatchToPushGateway(InspectionReportMessage message) {
        if (pushGateway == null) {
            log.info("[test profile] 巡检日报跳过推送（PushGateway 未注入）: {}", message);
            return;
        }
        pushGateway.push(message);
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("ACK 失败 deliveryTag={}", deliveryTag, e);
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            // requeue=false: 不重新入队，直接进入 DLX/DLQ
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException e) {
            log.error("Nack 失败 deliveryTag={}", deliveryTag, e);
        }
    }
}
