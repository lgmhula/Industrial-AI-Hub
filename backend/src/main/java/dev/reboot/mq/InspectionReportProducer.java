package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * AI 巡检日报消息生产者 — Agent 经此组件将日报投递到 RabbitMQ（Day 85，ADR 0031）。
 *
 * <p>本组件是 ADR 0031 六段链路中「Agent → RabbitMQ」的边界，
 * <b>不感知</b> SSE / Push Gateway / 浏览器连接 —— Producer 只做
 * {@code convertAndSend}，不持有任何 emitter 或 userId → 连接映射。
 * 任何 SseEmitter 依赖都属于 Phase 5 之后的 Push Gateway 层，不得反向注入本类。</p>
 *
 * <h3>失败策略（ADR 0031 §6）</h3>
 * <p>RabbitMQ 不可达时 {@link RabbitTemplate#convertAndSend} 抛
 * {@code AmqpException}，由调用方（Agent）catch 后降级：日志记录待发日报，
 * 可手动重投，<b>不阻塞 Agent 主流程</b>。本 Producer 不在内部吞异常，
 * 以便调用方明确感知投递结果。</p>
 *
 * <h3>与 {@link AlarmProducer} 的对齐</h3>
 * <ul>
 *   <li>同包 {@code dev.reboot.mq}；</li>
 *   <li>同 {@code @Component @Profile("!test")} —— 测试 profile 下不实例化，
 *       避免无 broker 时启动失败；</li>
 *   <li>同构造器注入 {@link RabbitTemplate}；</li>
 *   <li>同 {@code convertAndSend(exchange, routingKey, message)} 调用形态
 *       （Jackson2JsonMessageConverter 在 {@link MQConfig#rabbitTemplate} 已注入）。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-31 (Day 85, Phase 1)
 */
@Component
@Profile("!test")
public class InspectionReportProducer {

    private static final Logger log = LoggerFactory.getLogger(InspectionReportProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public InspectionReportProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 投递巡检日报消息到 {@code inspection.exchange → inspection.queue}。
     *
     * <p>调用方（Agent）需在 {@code generate()} 末尾构造
     * {@link InspectionReportMessage} 后调用本方法；本方法不阻塞主流程的
     * 约定由调用方实现 catch 降级（ADR 0031 §6 RabbitMQ 异常行）。</p>
     *
     * @param message 巡检日报消息（不可为 null）
     */
    public void send(InspectionReportMessage message) {
        rabbitTemplate.convertAndSend(
                MQConfig.INSPECTION_EXCHANGE,
                MQConfig.INSPECTION_ROUTING_KEY,
                message);
        log.info("AI 巡检日报消息已投递: {}", message);
    }
}
