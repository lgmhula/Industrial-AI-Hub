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
 * <h3>完整架构（Day 50-55 + Day 85）</h3>
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
 *  ├─ [Direct "alarm.delay.exchange"] ← Day 54 延迟队列
 *  │    └→ alarm.delay.queue (TTL 30s, 无消费者)
 *  │         → alarm.delay.dlx
 *  │           └→ alarm.escalation.queue → AlarmEscalationConsumer
 *  │
 *  └─ [Direct "inspection.exchange"]  ← Day 85 AI 巡检日报投递（ADR 0031）
 *       └→ inspection.queue (DLX→inspection.dlq, 无 TTL)
 *            → InspectionReportConsumer (Phase 2 实现)
 *              → Push Gateway → SSE → Vue（Phase 2-7 实现）
 * </pre>
 *
 * <h3>模式总结</h3>
 * <table>
 *   <tr><th>模式</th><th>Exchange</th><th>Day</th><th>场景</th></tr>
 *   <tr><td>工作队列</td><td>Direct</td><td>51</td><td>报警处理（竞争消费）</td></tr>
 *   <tr><td>发布/订阅</td><td>Fanout</td><td>52</td><td>数据同步（广播）</td></tr>
 *   <tr><td>死信队列</td><td>DLX</td><td>53</td><td>失败消息不丢失</td></tr>
 *   <tr><td>延迟队列</td><td>TTL+DLX</td><td>54</td><td>报警超时升级</td></tr>
 *   <tr><td>工作队列（无 TTL）</td><td>Direct</td><td>85</td><td>AI 巡检日报投递（ADR 0031）</td></tr>
 * </table>
 *
 * <h3>Day 85 inspection.* 设计要点（ADR 0031 §6.1 / §9）</h3>
 * <ul>
 *   <li>Direct Exchange + durable Queue —— 与 alarm.exchange 同结构，便于运维复用；</li>
 *   <li>DLX → inspection.dlq —— 失败日报不丢，可人工排查重投；</li>
 *   <li><b>不设 TTL</b> —— alarm.queue 30s TTL 适合「实时报警」场景，
 *       日报不应在 Consumer 短暂重启/慢处理时丢失，故省略 ttl()；</li>
 *   <li><b>不设 maxPriority</b> —— 日报无优先级需求（如需可后续添加）；</li>
 *   <li>acknowledge-mode 与 alarm 一致（manual，{@code application.yml} 控制）。</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51), 全景审查 2026-08-09 (Day 55), Day 85 增 inspection 队列
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

    // ── Day 85: AI 巡检日报投递（ADR 0031）— 与 alarm 同结构，无 TTL ──
    public static final String INSPECTION_EXCHANGE = "inspection.exchange";
    public static final String INSPECTION_QUEUE = "inspection.queue";
    public static final String INSPECTION_ROUTING_KEY = "inspection.report.new";
    public static final String INSPECTION_DLX = "inspection.dlx";
    public static final String INSPECTION_DLQ = "inspection.dlq";

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

    // ── Day 85: AI 巡检日报投递（ADR 0031）— Direct Exchange + DLX，无 TTL ──

    /**
     * 巡检日报 Direct Exchange（与 alarm.exchange 同结构，durable）。
     * <p>Producer 投递 {@code InspectionReportMessage} 到此 exchange，
     * 由 routing key {@link #INSPECTION_ROUTING_KEY} 路由到 {@link #inspectionQueue()}。</p>
     */
    @Bean
    public DirectExchange inspectionExchange() {
        return new DirectExchange(INSPECTION_EXCHANGE, true, false);
    }

    /**
     * 巡检日报工作队列（durable + DLX→inspection.dlx，<b>无 TTL</b>）。
     * <p>与 {@link #alarmQueue()} 的差异：
     * <ul>
     *   <li>省略 ttl() —— 日报延迟敏感度低，不应在 30s 内过期进 DLQ；</li>
     *   <li>省略 maxPriority —— 日报无优先级需求。</li>
     * </ul>
     * 其余（durable + DLX 路由）与 alarm.queue 完全一致。</p>
     */
    @Bean
    public Queue inspectionQueue() {
        return QueueBuilder.durable(INSPECTION_QUEUE)
                .deadLetterExchange(INSPECTION_DLX)
                .deadLetterRoutingKey(INSPECTION_ROUTING_KEY)
                .build();
    }

    /** 巡检日报死信 Exchange（与 alarm.dlx 同结构）。 */
    @Bean
    public DirectExchange inspectionDlxExchange() {
        return new DirectExchange(INSPECTION_DLX, true, false);
    }

    /** 巡检日报死信队列（durable，可人工排查重投）。 */
    @Bean
    public Queue inspectionDlq() {
        return QueueBuilder.durable(INSPECTION_DLQ).build();
    }

    /** 巡检日报 DLX → DLQ 绑定（与 alarm.dlqBinding 同模式）。 */
    @Bean
    public Binding inspectionDlqBinding() {
        return BindingBuilder.bind(inspectionDlq())
                .to(inspectionDlxExchange())
                .with(INSPECTION_ROUTING_KEY);
    }

    /** 巡检日报 Exchange → Queue 绑定（与 alarmBinding 同模式）。 */
    @Bean
    public Binding inspectionBinding() {
        return BindingBuilder.bind(inspectionQueue())
                .to(inspectionExchange())
                .with(INSPECTION_ROUTING_KEY);
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
