package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 报警消息消费者 — 从 RabbitMQ 工作队列异步消费报警消息。
 *
 * <p>多个实例可同时监听 {@code alarm.queue}，RabbitMQ 自动
 * round-robin 分发，实现水平扩展的报警处理能力。</p>
 *
 * <h3>当前行为（Day 51）</h3>
 * <p>仅记录日志。后续 Day 可扩展为：邮件通知、短信告警、
 * 自动创建工单、触发应急预案等。</p>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51)
 */
@Component
@Profile("!test")
public class AlarmConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlarmConsumer.class);

    /**
     * 监听报警队列，处理报警消息。
     *
     * <p>{@code concurrency = "2-4"}：启动 2-4 个消费者线程，
     * 工作队列模式下每条消息只被一个消费者处理。</p>
     *
     * @param message 报警消息
     */
    @RabbitListener(queues = MQConfig.ALARM_QUEUE, concurrency = "2-4")
    public void handleAlarm(AlarmMessage message) {
        log.warn("=== 异步报警处理 ===");
        log.warn("设备ID: {}", message.getDeviceId());
        log.warn("报警类型: {} (级别: {})", message.getAlarmType(), message.getAlarmLevel());
        log.warn("报警内容: {}", message.getAlarmMessage());
        log.warn("触发值: {} @ {}", message.getDataValue(), message.getTriggeredAt());
        log.warn("====================");

        // TODO Day 55: 扩展为实际的报警处理逻辑（通知、工单等）
    }
}
