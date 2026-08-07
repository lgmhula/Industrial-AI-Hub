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

    /** 报警 Direct Exchange。 */
    @Bean
    public DirectExchange alarmExchange() {
        return new DirectExchange(ALARM_EXCHANGE, true, false);
    }

    /** 报警工作队列。 */
    @Bean
    public Queue alarmQueue() {
        return QueueBuilder.durable(ALARM_QUEUE)
                .maxPriority(10)
                .build();
    }

    /** Exchange → Queue 绑定。 */
    @Bean
    public Binding alarmBinding() {
        return BindingBuilder.bind(alarmQueue())
                .to(alarmExchange())
                .with(ALARM_ROUTING_KEY);
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
