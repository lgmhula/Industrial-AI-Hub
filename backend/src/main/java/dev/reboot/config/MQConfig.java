package dev.reboot.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RabbitMQ 配置 —— Exchange / Queue / Binding 声明 + JSON 消息转换。
 *
 * <h3>架构</h3>
 * <pre>
 * Producer → [Direct Exchange "alarm.exchange"]
 *                │  routingKey = "alarm.new"
 *                ↓
 *           [Queue "alarm.queue"]
 *                │  (work-queue: 多 Consumer 竞争消费)
 *                ↓
 *           Consumer(s)
 * </pre>
 *
 * <h3>工作队列模式</h3>
 * <p>多个 Consumer 监听同一 Queue，RabbitMQ 默认 round-robin 分发。
 * 每个 Consumer 设置 prefetch=1，公平调度（能者多劳）。</p>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51)
 */
@Configuration
@Profile("!test")
public class MQConfig {

    public static final String ALARM_EXCHANGE = "alarm.exchange";
    public static final String ALARM_QUEUE = "alarm.queue";
    public static final String ALARM_ROUTING_KEY = "alarm.new";

    // ── Day 53: 死信队列 — 报警处理失败自动转入 DLQ ──
    public static final String ALARM_DLX = "alarm.dlx";
    public static final String ALARM_DLQ = "alarm.dlq";

    // ── Day 54: 延迟队列 — 报警 30s 未处理则升级 ──
    public static final String ALARM_DELAY_EXCHANGE = "alarm.delay.exchange";
    public static final String ALARM_DELAY_QUEUE = "alarm.delay.queue";
    public static final String ALARM_DELAY_DLX = "alarm.delay.dlx";
    public static final String ALARM_ESCALATION_QUEUE = "alarm.escalation.queue";
    public static final String ALARM_ESCALATION_KEY = "alarm.escalation";

    // ── Day 52: 发布/订阅 — 设备数据 Fanout Exchange ──
    public static final String DEVICE_DATA_FANOUT = "device-data.fanout";
    public static final String DEVICE_DATA_LOG_QUEUE = "device-data.log.queue";
    public static final String DEVICE_DATA_ANALYTICS_QUEUE = "device-data.analytics.queue";

    /** 报警 Direct Exchange。 */
    @Bean
    public DirectExchange alarmExchange() {
        return new DirectExchange(ALARM_EXCHANGE, true, false);
    }

    /** 报警工作队列（含死信策略：失败/过期 → alarm.dlx → alarm.dlq）。 */
    @Bean
    public Queue alarmQueue() {
        return QueueBuilder.durable(ALARM_QUEUE)
                .maxPriority(10)
                .deadLetterExchange(ALARM_DLX)
                .deadLetterRoutingKey(ALARM_ROUTING_KEY)
                .ttl(30_000)  // 消息 30s 未消费则过期进 DLQ
                .build();
    }

    // ── Day 53: 死信 Exchange / Queue ──

    @Bean
    public DirectExchange alarmDlxExchange() {
        return new DirectExchange(ALARM_DLX, true, false);
    }

    @Bean
    public Queue alarmDlq() {
        return QueueBuilder.durable(ALARM_DLQ).build();
    }

    @Bean
    public Binding alarmDlqBinding() {
        return BindingBuilder.bind(alarmDlq())
                .to(alarmDlxExchange())
                .with(ALARM_ROUTING_KEY);
    }

    // ── Day 54: 延迟队列（TTL 30s → DLX → escalation queue） ──

    @Bean
    public DirectExchange alarmDelayExchange() {
        return new DirectExchange(ALARM_DELAY_EXCHANGE, true, false);
    }

    /** 延迟 DLX — 接收 delay.queue 过期的消息。 */
    @Bean
    public DirectExchange alarmDelayDlx() {
        return new DirectExchange(ALARM_DELAY_DLX, true, false);
    }

    /** 升级队列 — 延迟消息过期后到达此处。 */
    @Bean
    public Queue alarmEscalationQueue() {
        return QueueBuilder.durable(ALARM_ESCALATION_QUEUE).build();
    }

    @Bean
    public Binding alarmEscalationBinding() {
        return BindingBuilder.bind(alarmEscalationQueue())
                .to(alarmDelayDlx())
                .with(ALARM_ESCALATION_KEY);
    }

    /**
     * 延迟队列 — 无消费者，消息在 TTL 过期后自动转入 DLX。
     * <p>per-message TTL 由 Producer 设置，此队列仅定义 DLX 路由。</p>
     */
    @Bean
    public Queue alarmDelayQueue() {
        return QueueBuilder.durable(ALARM_DELAY_QUEUE)
                .deadLetterExchange(ALARM_DELAY_DLX)
                .deadLetterRoutingKey(ALARM_ESCALATION_KEY)
                .build();
    }

    @Bean
    public Binding alarmDelayBinding() {
        return BindingBuilder.bind(alarmDelayQueue())
                .to(alarmDelayExchange())
                .with(ALARM_ESCALATION_KEY);
    }

    /** Exchange → Queue 绑定。 */
    @Bean
    public Binding alarmBinding() {
        return BindingBuilder.bind(alarmQueue())
                .to(alarmExchange())
                .with(ALARM_ROUTING_KEY);
    }

    // ── Day 52: Fanout Exchange + 多队列 ──

    @Bean
    public FanoutExchange deviceDataFanoutExchange() {
        return new FanoutExchange(DEVICE_DATA_FANOUT, true, false);
    }

    @Bean
    public Queue deviceDataLogQueue() {
        return QueueBuilder.durable(DEVICE_DATA_LOG_QUEUE).build();
    }

    @Bean
    public Queue deviceDataAnalyticsQueue() {
        return QueueBuilder.durable(DEVICE_DATA_ANALYTICS_QUEUE).build();
    }

    @Bean
    public Binding deviceDataLogBinding() {
        return BindingBuilder.bind(deviceDataLogQueue())
                .to(deviceDataFanoutExchange());
    }

    @Bean
    public Binding deviceDataAnalyticsBinding() {
        return BindingBuilder.bind(deviceDataAnalyticsQueue())
                .to(deviceDataFanoutExchange());
    }

    /** JSON 消息转换器（替代默认的 SimpleMessageConverter）。 */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** RabbitTemplate 配置 JSON 转换。 */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
