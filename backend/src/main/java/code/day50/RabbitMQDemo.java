package code.day50;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ 核心概念演示 — Exchange / Queue / Binding + 简单收发。
 *
 * <h3>架构</h3>
 * <pre>
 * Producer → [Direct Exchange "day50.exchange"]
 *                │  routingKey="day50.routing"
 *                ↓
 *           [Queue "day50.queue"]
 *                │
 *                ↓
 *           Consumer (异步回调)
 * </pre>
 *
 * <h3>Exchange 类型速查</h3>
 * <table>
 *   <tr><th>类型</th><th>路由规则</th><th>使用场景</th></tr>
 *   <tr><td>Direct</td><td>routingKey 精确匹配</td><td>本日 Demo</td></tr>
 *   <tr><td>Fanout</td><td>广播到所有绑定队列</td><td>设备数据同步到多个消费者</td></tr>
 *   <tr><td>Topic</td><td>routingKey 模式匹配 (*.#)</td><td>报警分级路由</td></tr>
 * </table>
 *
 * <h3>运行方式</h3>
 * <pre>
 * docker compose up -d rabbitmq
 * cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day50.RabbitMQDemo"
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 50)
 */
public class RabbitMQDemo {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQDemo.class);

    private static final String EXCHANGE_NAME = "day50.exchange";
    private static final String QUEUE_NAME = "day50.queue";
    private static final String ROUTING_KEY = "day50.routing";

    public static void main(String[] args) throws Exception {
        // ── 1. 连接 RabbitMQ ──
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("admin123");
        factory.setVirtualHost("/");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            log.info("=== RabbitMQ 核心概念演示 ===\n");

            // ── 2. 声明 Exchange (Direct 类型) ──
            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);
            log.info("1. Exchange 声明: name={} type=DIRECT durable=true", EXCHANGE_NAME);

            // ── 3. 声明 Queue ──
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            log.info("2. Queue 声明: name={} durable=true", QUEUE_NAME);

            // ── 4. 绑定 (Binding) → routingKey 精确匹配 ──
            channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
            log.info("3. Binding: {} → {} [routingKey={}]",
                    EXCHANGE_NAME, QUEUE_NAME, ROUTING_KEY);

            // ── 5. 发送消息 (Producer) ──
            String message = "Hello RabbitMQ! 时间戳: " + System.currentTimeMillis();
            channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes(StandardCharsets.UTF_8));
            log.info("4. 消息已发送: \"{}\"", message);

            // ── 6. 消费消息 (Consumer) ──
            log.info("5. 开始消费... (Ctrl+C 退出)");
            channel.basicConsume(QUEUE_NAME, true,
                    (consumerTag, delivery) -> {
                        String received = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        log.info("   ← 收到消息: \"{}\" (routingKey={})",
                                received, delivery.getEnvelope().getRoutingKey());
                    },
                    consumerTag -> log.info("消费取消: {}", consumerTag));

            // 等待消费回调
            Thread.sleep(3000);
            log.info("\n=== 演示结束 ===");
        }
    }
}
