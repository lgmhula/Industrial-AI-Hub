package dev.reboot.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RabbitMQ 配置 —— 全架构 Exchange / Queue / Binding 声明。
 *
 * <h3>完整架构（Day 50-55）</h3>
 * <pre>
 * DeviceDataService.report()
 *  │
 *  ├─ [Fanout "device-data.fanout"]  ← Day 52 发布/订阅
 *  │    ├→ device-data.log.queue      → DeviceDataSyncConsumer (归档)
 *  │    └→ device-data.analytics.queue → DeviceDataSyncConsumer (分析)
 *  │
 *  ├─ [Direct "alarm.exchange"]       ← Day 51 工作队列
 *  │    └→ alarm.queue (DLX→alarm.dlq) → AlarmConsumer (手动ACK+重试)
 *  │
 *  └─ [Direct "alarm.delay.exchange"] ← Day 54 延迟队列
 *       └→ alarm.delay.queue (TTL 30s, 无消费者)
 *            → alarm.delay.dlx
 *              └→ alarm.escalation.queue → AlarmEscalationConsumer
 * </pre>
 *
 * <h3>模式总结</h3>
 * <table>
 *   <tr><th>模式</th><th>Exchange</th><th>Day</th><th>场景</th></tr>
 *   <tr><td>工作队列</td><td>Direct</td><td>51</td><td>报警处理（竞争消费）</td></tr>
 *   <tr><td>发布/订阅</td><td>Fanout</td><td>52</td><td>数据同步（广播）</td></tr>
 *   <tr><td>死信队列</td><td>DLX</td><td>53</td><td>失败消息不丢失</td></tr>
 *   <tr><td>延迟队列</td><td>TTL+DLX</td><td>54</td><td>报警超时升级</td></tr>
 * </table>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51), 全景审查 2026-08-09 (Day 55)
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
