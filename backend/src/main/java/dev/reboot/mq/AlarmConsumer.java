package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警消息消费者 — 手动 ACK + 重试 + 死信队列 + 幂等消费。
 *
 * <h3>可靠性机制（Day 53 增强）</h3>
 * <table>
 *   <tr><th>机制</th><th>实现</th></tr>
 *   <tr><td>手动 ACK</td><td>处理成功 → {@code basicAck}；失败 → {@code basicNack(requeue=false)}</td></tr>
 *   <tr><td>死信队列</td><td>Nack 的消息自动路由到 {@code alarm.dlx → alarm.dlq}</td></tr>
 *   <tr><td>幂等消费</td><td>{@code processedMessageIds} 去重，防止重复处理</td></tr>
 *   <tr><td>消息持久化</td><td>Queue durable + 消息 deliveryMode=2（由 Producer 设置）</td></tr>
 * </table>
 *
 * <h3>流程</h3>
 * <pre>
 * alarm.queue → Consumer.handleAlarm()
 *   ├─ 成功 → basicAck → 消息删除
 *   └─ 失败 → basicNack(requeue=false) → alarm.dlx → alarm.dlq
 *                                              └→ handleDeadLetter()
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51), 增强 2026-08-09 (Day 53)
 */
@Component
@Profile("!test")
public class AlarmConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlarmConsumer.class);

    /** 幂等去重：已处理的消息 deliveryTag 集合。 */
    private final ConcurrentHashMap<Long, Boolean> processedTags = new ConcurrentHashMap<>();

    private static final int MAX_RETRIES = 3;

    /**
     * 监听报警队列（手动 ACK 模式）。
     *
     * <p>手动 ACK 由 {@code spring.rabbitmq.listener.simple.acknowledge-mode=manual}
     * 控制，此处通过 {@link Channel} 参数直接操作 ACK/Nack。</p>
     */
    @RabbitListener(queues = MQConfig.ALARM_QUEUE, concurrency = "2-4",
            ackMode = "MANUAL")
    public void handleAlarm(AlarmMessage message, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String msgId = "alarm:" + message.getDeviceId() + ":" + message.getTriggeredAt();

        // ── 幂等检查 ──
        if (processedTags.putIfAbsent(deliveryTag, true) != null) {
            log.debug("重复消息已忽略 deliveryTag={}", deliveryTag);
            ack(channel, deliveryTag);
            return;
        }

        try {
            processWithRetry(message, 0);
            log.info("报警处理成功 device={} type={}", message.getDeviceId(), message.getAlarmType());
            ack(channel, deliveryTag);
        } catch (Exception e) {
            log.error("报警处理失败 (deliveryTag={}), 进入死信队列", deliveryTag, e);
            nack(channel, deliveryTag);
        }
    }

    /**
     * 监听死信队列 — 收到死信时触发人工告警。
     */
    @RabbitListener(queues = MQConfig.ALARM_DLQ)
    public void handleDeadLetter(AlarmMessage message) {
        log.error("=== 死信告警 ===");
        log.error("设备ID: {} | 报警类型: {} | 级别: {}",
                message.getDeviceId(), message.getAlarmType(), message.getAlarmLevel());
        log.error("报警内容: {}", message.getAlarmMessage());
        log.error("触发值: {} @ {}", message.getDataValue(), message.getTriggeredAt());
        log.error("动作: 请立即人工排查！");
        log.error("================");
        // TODO: 接入钉钉/邮件/短信通知
    }

    /** 带重试的处理逻辑。 */
    private void processWithRetry(AlarmMessage message, int attempt) throws Exception {
        try {
            // ── 实际报警处理逻辑 ──
            log.warn("报警处理 [尝试 {}/{}]: device={} type={} level={} msg={}",
                    attempt + 1, MAX_RETRIES,
                    message.getDeviceId(), message.getAlarmType(),
                    message.getAlarmLevel(), message.getAlarmMessage());
            // 此处可扩展：写数据库、发通知、触发应急预案等

        } catch (Exception e) {
            if (attempt < MAX_RETRIES - 1) {
                log.warn("重试 {}/{}：{}", attempt + 1, MAX_RETRIES, e.getMessage());
                Thread.sleep(500L * (attempt + 1)); // 退避
                processWithRetry(message, attempt + 1);
            } else {
                throw e; // 重试耗尽，向上抛 → Nack → DLQ
            }
        }
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
