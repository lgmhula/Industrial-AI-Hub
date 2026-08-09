package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 设备数据同步消费者 — 发布/订阅模式，Fanout 广播。
 *
 * <p>一条设备数据被广播到两个队列，各消费者处理不同关注面：</p>
 * <ul>
 *   <li><b>log.queue</b>: 日志归档</li>
 *   <li><b>analytics.queue</b>: 实时分析</li>
 * </ul>
 *
 * <p>自 Day 55 起使用强类型 {@link DeviceDataMessage}，
 * 由 {@link DeviceDataProducer} 通过 Fanout Exchange 发布。</p>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 52), 重构 2026-08-09 (Day 55)
 */
@Component
@Profile("!test")
public class DeviceDataSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataSyncConsumer.class);

    /**
     * 日志归档 — 记录所有设备数据。
     */
    @RabbitListener(queues = MQConfig.DEVICE_DATA_LOG_QUEUE)
    public void handleLog(DeviceDataMessage message) {
        log.info("[数据归档] device={} type={} value={}{} @ {}",
                message.getDeviceId(), message.getDataType(),
                message.getDataValue(), message.getUnit(),
                message.getRecordedAt());
    }

    /**
     * 实时分析 — 趋势计算、异常检测。
     */
    @RabbitListener(queues = MQConfig.DEVICE_DATA_ANALYTICS_QUEUE)
    public void handleAnalytics(DeviceDataMessage message) {
        log.info("[实时分析] device={} type={} value={}{} — 更新趋势窗口",
                message.getDeviceId(), message.getDataType(),
                message.getDataValue(), message.getUnit());
    }
}
