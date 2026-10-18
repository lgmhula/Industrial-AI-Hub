package dev.reboot.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 巡检日报 Redis Pub/Sub 广播器。
 *
 * <p>RabbitMQ Consumer 在每个实例都会竞争消费消息，但 Redis SETNX 保证只有首个实例
 * 拿到幂等键。首个实例再把日报发布到 Redis channel，让所有实例的本地订阅者只向
 * 各自持有的 SSE emitter 推送。这样既保持全局幂等，又能覆盖多实例浏览器连接。</p>
 */
@Component
@Profile("!test")
public class InspectionPushPubSub implements MessageListener {

    public static final String CHANNEL = "industrial-ai-hub:inspection-report";

    private static final Logger log = LoggerFactory.getLogger(InspectionPushPubSub.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final InspectionPushGateway pushGateway;

    public InspectionPushPubSub(StringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                InspectionPushGateway pushGateway) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pushGateway = pushGateway;
    }

    /**
     * 发布日报到所有后端实例。
     *
     * @return true = 至少有一个实例订阅并接收；false = 发布失败或无订阅者，调用方可回退本机推送
     */
    public boolean publish(InspectionReportMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            Long subscribers = redis.convertAndSend(CHANNEL, payload);
            if (subscribers != null && subscribers > 0) {
                log.info("巡检日报已广播到 {} 个后端实例: {}", subscribers, message);
                return true;
            }
            log.warn("巡检日报广播无订阅者，将回退本机推送: {}", message);
            return false;
        } catch (Exception e) {
            log.warn("巡检日报 Redis 广播失败，将回退本机推送: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            InspectionReportMessage report = objectMapper.readValue(payload, InspectionReportMessage.class);
            pushGateway.push(report);
        } catch (Exception e) {
            log.error("巡检日报 Redis 广播消费失败: {}", e.getMessage(), e);
        }
    }
}
