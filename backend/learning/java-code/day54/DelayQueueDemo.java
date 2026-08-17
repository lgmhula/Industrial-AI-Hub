package code.day54;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.time.LocalTime;

/**
 * RabbitMQ 延迟队列演示 — TTL + DLX 实现定时消息。
 *
 * <h3>场景</h3>
 * <p>报警触发后 5 秒内未被确认则自动升级。
 * 用延迟队列替代定时轮询，零 CPU 开销。</p>
 *
 * <h3>原理</h3>
 * <pre>
 * Producer → [delay.queue]       (无消费者, x-message-ttl=5000, x-dead-letter-exchange=delay.dlx)
 *               │  5 秒后 TTL 过期
 *               ↓
 *           [delay.dlx] → [escalation.queue]
 *               │
 *               ↓
 *           EscalationConsumer: "报警升级！"
 * </pre>
 *
 * <h3>为什么不用定时任务</h3>
 * <table>
 *   <tr><th>方案</th><th>缺点</th></tr>
 *   <tr><td>@Scheduled 轮询 DB</td><td>CPU 空转、DB 压力、精度受调度间隔影响</td></tr>
 *   <tr><td>TTL + DLX</td><td>消息级延迟、零轮询、RabbitMQ 原生保证</td></tr>
 * </table>
 *
 * <h3>运行方式</h3>
 * <pre>
 * docker compose up -d rabbitmq
 * cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day54.DelayQueueDemo"
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-09 (Day 54)
 */
public class DelayQueueDemo {

    private static final Logger log = LoggerFactory.getLogger(DelayQueueDemo.class);

    private static final String DELAY_EXCHANGE = "day54.delay.exchange";
    private static final String DELAY_QUEUE = "day54.delay.queue";   // 无消费者！
    private static final String DELAY_DLX = "day54.delay.dlx";
    private static final String ESCALATION_QUEUE = "day54.escalation.queue";
    private static final String ROUTING_KEY = "alarm.escalation";

    /** 延迟时间（毫秒）— Demo 用 5 秒，生产 30 秒。 */
    private static final int DELAY_MS = 5_000;

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("change_me");

        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {

            // ── 1. 声明 escalation Exchange / Queue ──
            ch.exchangeDeclare(DELAY_DLX, BuiltinExchangeType.DIRECT, true);
            ch.queueDeclare(ESCALATION_QUEUE, true, false, false, null);
            ch.queueBind(ESCALATION_QUEUE, DELAY_DLX, ROUTING_KEY);

            // ── 2. 声明 delay Exchange ──
            ch.exchangeDeclare(DELAY_EXCHANGE, BuiltinExchangeType.DIRECT, true);

            // ── 3. 声明 delay Queue（核心：无消费者 + TTL 过期 → DLX） ──
            Map<String, Object> queueArgs = new HashMap<>();
            queueArgs.put("x-dead-letter-exchange", DELAY_DLX);
            queueArgs.put("x-dead-letter-routing-key", ROUTING_KEY);
            queueArgs.put("x-message-ttl", DELAY_MS);
            ch.queueDeclare(DELAY_QUEUE, true, false, false, queueArgs);
            ch.queueBind(DELAY_QUEUE, DELAY_EXCHANGE, ROUTING_KEY);
            log.info("延迟队列就绪: {} (TTL={}ms) → {} → {}",
                    DELAY_QUEUE, DELAY_MS, DELAY_DLX, ESCALATION_QUEUE);
            log.info("");

            // ── 4. 启动 escalation Consumer ──
            CountDownLatch latch = new CountDownLatch(3);
            startEscalationConsumer(factory, latch);

            // ── 5. 发送 3 条报警（模拟需要延迟检查） ──
            try (Channel prodCh = conn.createChannel()) {
                log.info("=== 发送 3 条报警（需 {}s 后检查是否已处理） ===", DELAY_MS / 1000);
                for (int i = 1; i <= 3; i++) {
                    String body = String.format("报警#%d: PLC-%d 温度过高 @ %s",
                            i, i, LocalTime.now().toString().substring(0, 8));
                    prodCh.basicPublish(DELAY_EXCHANGE, ROUTING_KEY,
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            body.getBytes(StandardCharsets.UTF_8));
                    log.info("  → 发送延迟消息 #{}: {}", i, body);
                }
                log.info("");
            }

            // ── 6. 等待 escalation ──
            log.info("等待 {}s 后延迟消息过期...", DELAY_MS / 1000);
            boolean done = latch.await(DELAY_MS + 10_000, TimeUnit.MILLISECONDS);

            log.info("\n=== 延迟队列结果 ===");
            log.info("发送: 3 条延迟消息, TTL={}s, 全部升级: {}", DELAY_MS / 1000, done);
            log.info("→ 延迟队列不需要 @Scheduled 轮询，RabbitMQ 自动触发");
        }
    }

    private static void startEscalationConsumer(ConnectionFactory factory,
                                                 CountDownLatch latch) {
        Thread t = new Thread(() -> {
            try (Connection conn = factory.newConnection();
                 Channel ch = conn.createChannel()) {
                ch.basicConsume(ESCALATION_QUEUE, true, (consumerTag, delivery) -> {
                    String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    log.warn("  ⚠ 报警升级! {} (已等待 {}s)", body, DELAY_MS / 1000);
                    latch.countDown();
                }, consumerTag -> {});
                try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                log.error("升级消费者异常", e);
            }
        }, "escalation-consumer");
        t.setDaemon(true);
        t.start();
    }
}
