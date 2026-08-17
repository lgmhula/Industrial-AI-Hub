package code.day53;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 消息可靠性演示 — 手动 ACK + 死信队列 + 幂等消费。
 *
 * <h3>四种可靠性机制</h3>
 * <table>
 *   <tr><th>机制</th><th>作用</th><th>本 Demo 实现</th></tr>
 *   <tr><td>消息持久化</td><td>Broker 重启不丢消息</td><td>durable=true + PERSISTENT</td></tr>
 *   <tr><td>手动 ACK</td><td>Consumer 确认后才删除</td><td>autoAck=false + basicAck</td></tr>
 *   <tr><td>死信队列</td><td>失败消息不丢弃，转入 DLQ</td><td>x-dead-letter-exchange</td></tr>
 *   <tr><td>幂等消费</td><td>重复消费不产生副作用</td><td>processedIds Set 去重</td></tr>
 * </table>
 *
 * <h3>架构</h3>
 * <pre>
 * Producer → [day53.task.queue]
 *                │  Consumer 处理失败 (basicNack/basicReject)
 *                ↓
 *           [day53.task.dlx]  (Dead Letter Exchange)
 *                │
 *                ↓
 *           [day53.task.dlq]  (Dead Letter Queue)
 *                │
 *           DLQ Consumer (告警/人工处理)
 * </pre>
 *
 * <h3>运行方式</h3>
 * <pre>
 * docker compose up -d rabbitmq
 * cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day53.ReliableMessagingDemo"
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 53)
 */
public class ReliableMessagingDemo {

    private static final Logger log = LoggerFactory.getLogger(ReliableMessagingDemo.class);

    private static final String TASK_EXCHANGE = "day53.task.exchange";
    private static final String TASK_QUEUE = "day53.task.queue";
    private static final String TASK_DLX = "day53.task.dlx";   // Dead Letter Exchange
    private static final String TASK_DLQ = "day53.task.dlq";   // Dead Letter Queue
    private static final String ROUTING_KEY = "task.new";

    // ── 幂等消费：已处理的消息 ID 集合 ──
    private static final ConcurrentHashMap<String, Boolean> processedIds = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("change_me");

        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {

            // ── 1. 声明 Dead Letter Exchange / Queue ──
            ch.exchangeDeclare(TASK_DLX, BuiltinExchangeType.DIRECT, true);
            ch.queueDeclare(TASK_DLQ, true, false, false, null);
            ch.queueBind(TASK_DLQ, TASK_DLX, ROUTING_KEY);

            // ── 2. 声明主 Exchange ──
            ch.exchangeDeclare(TASK_EXCHANGE, BuiltinExchangeType.DIRECT, true);

            // ── 3. 声明主 Queue（配置死信策略） ──
            Map<String, Object> queueArgs = new HashMap<>();
            queueArgs.put("x-dead-letter-exchange", TASK_DLX);
            queueArgs.put("x-dead-letter-routing-key", ROUTING_KEY);
            // 消息在队列中最多存活 10 秒，超时也进 DLQ
            queueArgs.put("x-message-ttl", 10_000);
            ch.queueDeclare(TASK_QUEUE, true, false, false, queueArgs);
            ch.queueBind(TASK_QUEUE, TASK_EXCHANGE, ROUTING_KEY);
            log.info("死信队列架构已建立: {} → (失败)→ {} → {}", TASK_QUEUE, TASK_DLX, TASK_DLQ);
            log.info("");

            // ── 4. 发送 5 条消息（其中 #3 故意失败） ──
            int total = 5;
            CountDownLatch latch = new CountDownLatch(total);
            log.info("=== Producer 发送 {} 条任务 ===", total);
            for (int i = 1; i <= total; i++) {
                String msgId = "MSG-" + i;
                String body = "任务 #" + i + " (id=" + msgId + ")";
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .messageId(msgId)
                        .deliveryMode(2) // persistent
                        .build();
                ch.basicPublish(TASK_EXCHANGE, ROUTING_KEY, props,
                        body.getBytes(StandardCharsets.UTF_8));
                log.info("  → 发送 {}: {}", msgId, body);
            }
            log.info("");

            // ── 5. 主 Consumer：手动 ACK，失败进入 DLQ ──
            Channel consumerCh = conn.createChannel();
            consumerCh.basicQos(1);

            int[] failCount = {0};
            consumerCh.basicConsume(TASK_QUEUE, false, (consumerTag, delivery) -> {
                String msgId = delivery.getProperties().getMessageId();
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);

                // ── 幂等检查 ──
                if (processedIds.putIfAbsent(msgId, true) != null) {
                    log.warn("  主Consumer: 重复消息 {} 已忽略", msgId);
                    consumerCh.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    latch.countDown();
                    return;
                }

                // ── 模拟处理：MSG-3 故意失败 → Nack → 进入 DLQ ──
                boolean success = !"MSG-3".equals(msgId);
                if (success) {
                    log.info("  主Consumer: {} 处理成功 ✓", msgId);
                    consumerCh.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else {
                    failCount[0]++;
                    log.warn("  主Consumer: {} 处理失败 ✗ → Nack → 进入 DLQ", msgId);
                    // requeue=false: 不重新入队，直接进入 DLX/DLQ
                    consumerCh.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                }
                latch.countDown();
            }, consumerTag -> {});

            // ── 6. DLQ Consumer：监听死信 ──
            Channel dlqCh = conn.createChannel();
            CountDownLatch dlqLatch = new CountDownLatch(1);
            dlqCh.basicConsume(TASK_DLQ, true, (consumerTag, delivery) -> {
                String msgId = delivery.getProperties().getMessageId();
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                log.warn("  DLQ Consumer: 收到死信 {} — 内容: {} — 触发人工告警!", msgId, body);
                dlqLatch.countDown();
            }, consumerTag -> {});

            // ── 等待 ──
            latch.await(15, TimeUnit.SECONDS);
            dlqLatch.await(5, TimeUnit.SECONDS);

            log.info("\n=== 可靠性演示结果 ===");
            log.info("发送: {} 条, 成功: {} 条, 失败进DLQ: {} 条", total, total - failCount[0], failCount[0]);
            log.info("→ 持久化: Exchange/Queue 均为 durable, 消息 deliveryMode=2");
            log.info("→ 手动ACK: 成功 basicAck, 失败 basicNack(requeue=false)");
            log.info("→ 死信队列: 失败消息自动路由到 DLQ, 触发告警");
            log.info("→ 幂等消费: processedIds ConcurrentHashMap 去重");

            consumerCh.close();
            dlqCh.close();
        }
    }
}
