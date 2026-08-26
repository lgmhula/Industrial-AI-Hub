package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 报警升级消费者 — 监听延迟队列过期的消息。
 *
 * <h3>工作原理</h3>
 * <pre>
 * 报警触发 → AlarmProducer.sendDelayCheck()
 *   → alarm.delay.queue (TTL 30s, 无消费者)
 *     → 30s 后过期
 *       → alarm.delay.dlx → alarm.escalation.queue
 *         → AlarmEscalationConsumer.handleEscalation()
 *           → 检查报警是否已处理
 *             ├─ 已处理: 忽略
 *             └─ 未处理: 升级告警!
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 54)
 */
@Component
@Profile("!test")
public class AlarmEscalationConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlarmEscalationConsumer.class);

    /**
     * 监听升级队列 — 延迟消息 30s 过期后到达此处。
     *
     * <p>理论上此处应查询数据库确认报警是否已被 ack/resolve，
     * 当前 Day 54 先实现完整链路，Day 55 补齐 DB 查询。</p>
     */
    @RabbitListener(queues = MQConfig.ALARM_ESCALATION_QUEUE)
    public void handleEscalation(AlarmMessage message) {
        log.warn("=== 报警升级 ===");
        log.warn("设备ID: {} | 报警类型: {} | 级别: {}",
                message.getDeviceId(), message.getAlarmType(), message.getAlarmLevel());
        log.warn("报警内容: {}", message.getAlarmMessage());
        log.warn("触发值: {} @ {}", message.getDataValue(), message.getTriggeredAt());
        log.warn("原因: 报警触发 30 秒后仍未处理");
        log.warn("动作: 升级通知（钉钉/邮件/短信）");
        log.warn("==============");

        // TODO Day 55: 查询 DB 确认报警状态，避免误升级
        // TODO: 接入钉钉/邮件/短信通知
    }
}
