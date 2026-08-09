package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 设备数据生产者 — 通过 Fanout Exchange 广播设备数据到下游系统。
 *
 * <h3>发布/订阅模式</h3>
 * <p>一条设备数据消息通过 {@code device-data.fanout} 广播到：
 * {@code device-data.log.queue}（日志归档）和
 * {@code device-data.analytics.queue}（实时分析）。</p>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 55)
 */
@Component
@Profile("!test")
public class DeviceDataProducer {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public DeviceDataProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 广播设备数据到所有订阅者。
     *
     * @param message 设备数据
     */
    public void publish(DeviceDataMessage message) {
        rabbitTemplate.convertAndSend(
                MQConfig.DEVICE_DATA_FANOUT,
                "",  // Fanout 忽略 routingKey
                message);
        log.debug("设备数据已广播: {}", message);
    }
}
