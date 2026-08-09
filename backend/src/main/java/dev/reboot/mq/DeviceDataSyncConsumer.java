package dev.reboot.mq;

import dev.reboot.config.MQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 设备数据同步消费者 — 发布/订阅模式。
 *
 * <p>一条设备数据消息通过 Fanout Exchange 广播到多个队列，
 * 各消费者独立处理不同关注面：</p>
 * <ul>
 *   <li><b>log.queue</b>: 日志归档 — 记录原始数据</li>
 *   <li><b>analytics.queue</b>: 实时分析 — 趋势计算、异常检测</li>
 * </ul>
 *
 * <h3>Fanout vs Work-Queue</h3>
 * <table>
 *   <tr><th>模式</th><th>路由</th><th>Day</th><th>示例</th></tr>
 *   <tr><td>Work-Queue</td><td>一条消息 → 一个消费者</td><td>Day 51</td><td>报警处理</td></tr>
 *   <tr><td>Fanout</td><td>一条消息 → 所有消费者</td><td>Day 52</td><td>数据同步</td></tr>
 * </table>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 52)
 */
@Component
@Profile("!test")
public class DeviceDataSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataSyncConsumer.class);

    /**
     * 日志归档消费者 — 记录所有设备数据。
     */
    @RabbitListener(queues = MQConfig.DEVICE_DATA_LOG_QUEUE)
    public void handleLog(String message) {
        log.info("[数据归档] 写入归档日志: {}", message);
    }

    /**
     * 实时分析消费者 — 可用于趋势计算、异常检测。
     */
    @RabbitListener(queues = MQConfig.DEVICE_DATA_ANALYTICS_QUEUE)
    public void handleAnalytics(String message) {
        log.info("[实时分析] 检测数据趋势: {}", message);
    }
}
