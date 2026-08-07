package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 报警消息生产者 — 将报警事件异步发送到 RabbitMQ 工作队列。
 *
 * <p>当 {@link dev.reboot.service.AlarmDetector} 检测到报警后，
 * 由调用方（DeviceDataService）通过本组件将报警信息投递到
 * {@code alarm.exchange → alarm.queue}，实现报警处理的异步解耦。</p>
 *
 * <h3>工作队列模式</h3>
 * <p>多个 Consumer 竞争消费同一队列，RabbitMQ 默认 round-robin 分发，
 * 实现报警处理的负载均衡。</p>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51)
 */
@Component
@Profile("!test")
public class AlarmProducer {

    private static final Logger log = LoggerFactory.getLogger(AlarmProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public AlarmProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送报警消息到 RabbitMQ。
     *
     * @param message 报警消息
     */
    public void send(AlarmMessage message) {
        rabbitTemplate.convertAndSend(
                MQConfig.ALARM_EXCHANGE,
                MQConfig.ALARM_ROUTING_KEY,
                message);
        log.info("报警消息已发送: {}", message);
    }
}
