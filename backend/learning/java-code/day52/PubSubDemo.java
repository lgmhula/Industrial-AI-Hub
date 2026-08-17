package code.day52;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 发布/订阅模式演示 — Fanout Exchange 广播。
 *
 * <h3>场景</h3>
 * <p>工业设备数据上报后需要同步到多个下游系统：
 * 日志归档、实时分析、告警通知。一条消息，三个 Consumer 同时收到。</p>
 *
 * <h3>架构</h3>
 * <pre>
 *                    ┌→ [log.queue]    → LogConsumer
 * Producer → [Fanout Exchange] → [analytics.queue] → AnalyticsConsumer
 *                    └→ [notify.queue]  → NotifyConsumer
 * </pre>
 *
 * <h3>Direct vs Fanout 对比</h3>
 * <table>
 *   <tr><th>Direct</th><td>routingKey 精确匹配，一条消息 → 一个队列</td></tr>
 *   <tr><th>Fanout</th><td>忽略 routingKey，一条消息 → 所有绑定队列</td></tr>
 * </table>
 *
 * <h3>运行方式</h3>
 * <pre>
 * docker compose up -d rabbitmq
 * cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day52.PubSubDemo"
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 52)
 */
public class PubSubDemo {

    private static final Logger log = LoggerFactory.getLogger(PubSubDemo.class);

    private static final String FANOUT_EXCHANGE = "day52.data.fanout";
    private static final String[] QUEUES = {
            "day52.data.log", "day52.data.analytics", "day52.data.notify"
    };

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("change_me");

        int msgCount = 3;
        CountDownLatch latch = new CountDownLatch(msgCount * QUEUES.length);

        try (Connection conn = factory.newConnection();
             Channel setupCh = conn.createChannel()) {

            // ── 声明 Fanout Exchange + 3 个 Queue + 绑定 ──
            setupCh.exchangeDeclare(FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);
            log.info("Fanout Exchange 声明: {}", FANOUT_EXCHANGE);

            for (String q : QUEUES) {
                setupCh.queueDeclare(q, true, false, false, null);
                setupCh.queueBind(q, FANOUT_EXCHANGE, ""); // Fanout 忽略 routingKey
                log.info("Queue 绑定: {} → {}", q, FANOUT_EXCHANGE);
            }
            log.info("");

            // ── 启动 3 个 Consumer（各自独立 Connection + Channel） ──
            String[] labels = {"Log", "Analytics", "Notify"};
            for (int i = 0; i < QUEUES.length; i++) {
                startConsumer(factory, QUEUES[i], labels[i], latch);
            }

            // ── Producer: 发送设备数据消息 ──
            try (Channel prodCh = conn.createChannel()) {
                log.info("=== Producer 发送 {} 条设备数据 ===", msgCount);
                for (int i = 1; i <= msgCount; i++) {
                    String body = String.format("PLC-%d 温度=%.1f°C 压力=%.2fMPa @ %s",
                            i, 60 + Math.random() * 40, 0.5 + Math.random() * 2,
                            java.time.LocalTime.now().toString().substring(0, 8));
                    prodCh.basicPublish(FANOUT_EXCHANGE, "",
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            body.getBytes(StandardCharsets.UTF_8));
                    log.info("  → 发送 #{}: {}", i, body);
                }
                log.info("");
            }

            // ── 等待所有 Consumer 处理完毕 ──
            boolean done = latch.await(15, TimeUnit.SECONDS);
            log.info("\n=== 发布/订阅结果 ===");
            log.info("发送: {} 条, 期望投递: {} 条 ({} × {})",
                    msgCount, msgCount * QUEUES.length, msgCount, QUEUES.length);
            log.info("全部确认: {}", done);
            log.info("→ Fanout Exchange 将每条消息广播到了全部 {} 个队列", QUEUES.length);
        }
    }

    private static void startConsumer(ConnectionFactory factory, String queue,
                                       String label, CountDownLatch latch) {
        Thread t = new Thread(() -> {
            try (Connection conn = factory.newConnection();
                 Channel ch = conn.createChannel()) {
                ch.basicConsume(queue, true, (consumerTag, delivery) -> {
                    String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    log.info("  [{}] ← {}", label, body);
                    latch.countDown();
                }, consumerTag -> {});
                try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                log.error("[{}] 异常", label, e);
            }
        }, "consumer-" + label);
        t.setDaemon(true);
        t.start();
    }
}
