package code.day51;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RabbitMQ 工作队列模式演示 — 多消费者竞争消费。
 *
 * <h3>场景</h3>
 * <p>模拟工业场景：3 台 PLC 同时上报温度数据，2 个报警处理 worker
 * 竞争消费报警队列，RabbitMQ 自动 round-robin 分发。</p>
 *
 * <h3>架构</h3>
 * <pre>
 * Producer Thread (发送 10 条报警消息)
 *        │
 *        ↓
 * [Direct Exchange "day51.alarm.exchange"]
 *        │  routingKey = "day51.alarm"
 *        ↓
 * [Queue "day51.alarm.queue"]
 *   ↙              ↘
 * Worker-1        Worker-2    (各处理约 5 条)
 * </pre>
 *
 * <h3>要点</h3>
 * <ul>
 *   <li>prefetch=1：公平调度，处理慢的 worker 不会堆积消息</li>
 *   <li>手动 ACK：处理完成后确认，保证消息不丢失</li>
 *   <li>durable=true：队列和消息持久化到磁盘</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * <pre>
 * docker compose up -d rabbitmq
 * cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day51.WorkQueueDemo"
 * </pre>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51)
 */
public class WorkQueueDemo {

    private static final Logger log = LoggerFactory.getLogger(WorkQueueDemo.class);

    private static final String EXCHANGE = "day51.alarm.exchange";
    private static final String QUEUE = "day51.alarm.queue";
    private static final String ROUTING_KEY = "day51.alarm";
    private static final int MESSAGE_COUNT = 10;
    private static final int WORKER_COUNT = 2;

    private static final AtomicInteger processedA = new AtomicInteger(0);
    private static final AtomicInteger processedB = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("change_me");

        try (Connection conn = factory.newConnection()) {
            // ── 声明 Exchange / Queue / Binding ──
            try (Channel setupCh = conn.createChannel()) {
                setupCh.exchangeDeclare(EXCHANGE, BuiltinExchangeType.DIRECT, true);
                setupCh.queueDeclare(QUEUE, true, false, false, null);
                setupCh.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);
                log.info("Exchange/Queue 声明完成\n");
            }

            // ── 启动 2 个 Worker（各自独立 Channel + Connection） ──
            CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
            Thread workerA = startWorker(factory, "Worker-1", processedA, latch);
            Thread workerB = startWorker(factory, "Worker-2", processedB, latch);

            // ── Producer: 发送 10 条模拟报警消息 ──
            try (Channel prodCh = conn.createChannel()) {
                log.info("=== Producer 开始发送 {} 条报警消息 ===", MESSAGE_COUNT);
                for (int i = 1; i <= MESSAGE_COUNT; i++) {
                    String body = String.format("PLC-%d 温度过高: %.1f°C @ %s",
                            (i % 3) + 1, 85.0 + Math.random() * 20, java.time.LocalTime.now());
                    prodCh.basicPublish(EXCHANGE, ROUTING_KEY,
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            body.getBytes(StandardCharsets.UTF_8));
                    log.info("  → 发送 #{}: {}", i, body);
                }
                log.info("");
            }

            // ── 等待所有消息处理完毕 ──
            boolean done = latch.await(30, TimeUnit.SECONDS);
            log.info("\n=== 工作队列结果 ===");
            log.info("Worker-1 处理: {} 条", processedA.get());
            log.info("Worker-2 处理: {} 条", processedB.get());
            log.info("总计: {} 条 (全部确认: {})", MESSAGE_COUNT, done);

            workerA.interrupt();
            workerB.interrupt();
        }
    }

    private static Thread startWorker(ConnectionFactory factory, String name,
                                       AtomicInteger counter, CountDownLatch latch) {
        Thread t = new Thread(() -> {
            try (Connection conn = factory.newConnection();
                 Channel ch = conn.createChannel()) {

                // prefetch=1：每次只取 1 条，处理完再取下一条（公平调度）
                ch.basicQos(1);

                ch.basicConsume(QUEUE, false, // autoAck=false → 手动确认
                        (consumerTag, delivery) -> {
                            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                            counter.incrementAndGet();

                            // 模拟处理耗时（Worker-1 比 Worker-2 慢 2 倍）
                            long delay = name.equals("Worker-1") ? 400 : 200;
                            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}

                            log.info("{} ← 处理: {} (耗时 {}ms)", name, body, delay);

                            // 手动 ACK：确认消息已处理
                            ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                            latch.countDown();
                        },
                        consumerTag -> {});

                // 保持线程存活
                try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}

            } catch (Exception e) {
                log.error("{} 异常", name, e);
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
