package code.day97;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Day 97 — 多设备并发 MQTT 压力测试。
 *
 * <p>模拟 N 台设备并发向 EMQX 发布遥测数据，验证：
 * <ol>
 *   <li>多 Topic 并发发布吞吐量（msgs/sec）与 Paho 确认延迟；</li>
 *   <li>QoS 1 deliveryComplete 回调在高频下的可靠性；</li>
 *   <li>幂等键碰撞测试：同一 deviceCode + 同一秒级 ts 发两次，后端 Redis SETNX 应去重；</li>
 *   <li>连接稳定性（无断连、无 NPE 断链）。</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>
 *   java code.day97.MultiDeviceStressTest                           # 10 设备 × 5 msg/s × 30s
 *   java code.day97.MultiDeviceStressTest 20 10 60                  # 20 设备 × 10 msg/s × 60s
 *   java code.day97.MultiDeviceStressTest tcp://localhost:1883 5 2 15  # broker 显式传入
 * </pre>
 *
 * @author AI 助手
 * @since 2026-09-05 (Day 97)
 */
public class MultiDeviceStressTest {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Shanghai"));
    private static final String SITE_CODE = "PLANT_A";
    private static final String DEVICE_PREFIX = "PLC-STRESS-";
    private static final String PAYLOAD_VERSION = "1.0";

    private final String broker;
    private final int deviceCount;
    private final int ratePerSecond;
    private final int durationSeconds;
    private final boolean verbose;

    // 聚合指标
    private final AtomicLong totalSent = new AtomicLong();
    private final AtomicLong totalConfirmed = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();
    private final List<Long> latencySamples = Collections.synchronizedList(new ArrayList<>());

    // 幂等测试指标
    private final AtomicInteger dupSent = new AtomicInteger();
    private final AtomicInteger dupConfirmed = new AtomicInteger();

    public MultiDeviceStressTest(String broker, int deviceCount, int ratePerSecond,
                                int durationSeconds, boolean verbose) {
        this.broker = broker;
        this.deviceCount = deviceCount;
        this.ratePerSecond = ratePerSecond;
        this.durationSeconds = durationSeconds;
        this.verbose = verbose;
    }

    public static void main(String[] args) throws Exception {
        String broker = "tcp://localhost:1883";
        int devices = 10;
        int rate = 5;
        int duration = 30;
        boolean verbose = false;

        int[] positionals = new int[3];
        int pIdx = 0;
        for (String arg : args) {
            if (arg.startsWith("tcp://") || arg.startsWith("ssl://") || arg.startsWith("ws://")) {
                broker = arg;
            } else if (arg.equals("-v") || arg.equals("--verbose")) {
                verbose = true;
            } else if (arg.matches("\\d+") && pIdx < 3) {
                positionals[pIdx++] = Integer.parseInt(arg);
            }
        }
        if (pIdx >= 1) devices = positionals[0];
        if (pIdx >= 2) rate = positionals[1];
        if (pIdx >= 3) duration = positionals[2];

        System.out.println("=== Day 97 Multi-Device Stress Test ===");
        System.out.println("broker=" + broker + " devices=" + devices
                + " rate=" + rate + "msg/s duration=" + duration + "s");
        System.out.println("expected total ≈ " + (devices * rate * duration) + " messages");
        System.out.println();

        MultiDeviceStressTest test = new MultiDeviceStressTest(
                broker, devices, rate, duration, verbose);
        test.run();
    }

    public void run() throws Exception {
        ExecutorService devicePool = Executors.newFixedThreadPool(deviceCount);
        List<DeviceWorker> workers = new ArrayList<>();
        CountDownLatch allStarted = new CountDownLatch(deviceCount);

        for (int i = 1; i <= deviceCount; i++) {
            String deviceCode = String.format("%s%03d", DEVICE_PREFIX, i);
            DeviceWorker worker = new DeviceWorker(deviceCode, i, allStarted);
            workers.add(worker);
            devicePool.submit(worker);
        }

        // 等待所有设备连接
        allStarted.await(30, TimeUnit.SECONDS);
        System.out.println("[main] " + deviceCount + " devices connected, starting stress phase...");
        System.out.println();

        // 运行 durationSeconds 后停止
        Thread.sleep(durationSeconds * 1000L);

        System.out.println("\n[main] stopping all devices...");
        for (DeviceWorker w : workers) {
            w.stop();
        }
        devicePool.shutdown();
        devicePool.awaitTermination(10, TimeUnit.SECONDS);

        printSummary();
    }

    private void printSummary() {
        long sent = totalSent.get();
        long confirmed = totalConfirmed.get();
        long failed = totalFailed.get();
        double actualRate = sent > 0 ? (double) sent / durationSeconds : 0;
        double confirmRate = sent > 0 ? (double) confirmed / sent * 100 : 0;
        double failRate = sent > 0 ? (double) failed / sent * 100 : 0;

        System.out.println();
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║     Day 97 Stress Test — Summary Report    ║");
        System.out.println("╠════════════════════════════════════════════╣");
        System.out.printf ("║  Devices            : %5d                  ║%n", deviceCount);
        System.out.printf ("║  Duration           : %5ds                  ║%n", durationSeconds);
        System.out.printf ("║  Target rate/device : %5d msg/s             ║%n", ratePerSecond);
        System.out.printf ("║  Total sent         : %5d                  ║%n", sent);
        System.out.printf ("║  Total confirmed    : %5d (%.1f%%)          ║%n", confirmed, confirmRate);
        System.out.printf ("║  Total failed       : %5d (%.1f%%)          ║%n", failed, failRate);
        System.out.printf ("║  Actual throughput  : %.1f msg/s           ║%n", actualRate);
        System.out.printf ("║  Dup sent/confirmed : %d / %d              ║%n",
                dupSent.get(), dupConfirmed.get());

        if (!latencySamples.isEmpty()) {
            List<Long> sorted = new ArrayList<>(latencySamples);
            Collections.sort(sorted);
            double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
            long p50 = sorted.get(sorted.size() / 2);
            long p95 = sorted.get((int) (sorted.size() * 0.95));
            long p99 = sorted.get((int) (sorted.size() * 0.99));
            long max = sorted.get(sorted.size() - 1);
            System.out.printf ("║  Latency avg        : %.2f ms               ║%n", avg);
            System.out.printf ("║  Latency p50/p95/p99: %d / %d / %d ms       ║%n", p50, p95, p99);
            System.out.printf ("║  Latency max        : %d ms                ║%n", max);
        }

        boolean pass = failed == 0 && confirmed == sent;
        System.out.printf ("║  Result             : %s                    ║%n",
                pass ? "✅ PASS" : "❌ FAIL");
        System.out.println("╚════════════════════════════════════════════╝");

        // 幂等测试结论
        System.out.println();
        System.out.println("[idempotency] 发送 " + dupSent.get() + " 条重复时间戳消息，"
                + dupConfirmed.get() + " 条 Paho 确认。");
        System.out.println("[idempotency] 后端 Redis SETNX 应按 mqtt:{deviceId}:{ts}:{dataType} 去重，");
        System.out.println("[idempotency] 实际去重效果需在后端日志 / device_data 行数中验证。");
    }

    /**
     * 单设备工作线程：连接 EMQX 后按固定频率 publish 遥测。
     */
    private final class DeviceWorker implements Runnable, MqttCallback {

        private final String deviceCode;
        private final int deviceIndex;
        private final CountDownLatch startLatch;
        private final Random random;
        private final String persistenceDir;
        private final long[] sendTimestamps;

        private volatile MqttClient client;
        private volatile ScheduledExecutorService scheduler;
        private ScheduledFuture<?> publishTask;
        private volatile boolean stopping;

        // 本设备指标
        private final AtomicLong deviceSent = new AtomicLong();
        private final AtomicLong deviceConfirmed = new AtomicLong();
        // 确认计数器：QoS 1 下同 client 的 deliveryComplete 按发送顺序回调
        private final AtomicLong confirmSeq = new AtomicLong();

        DeviceWorker(String deviceCode, int deviceIndex, CountDownLatch startLatch) {
            this.deviceCode = deviceCode;
            this.deviceIndex = deviceIndex;
            this.startLatch = startLatch;
            this.random = new Random(deviceCode.hashCode());
            this.persistenceDir = System.getProperty("java.io.tmpdir")
                    + File.separator + "paho-stress-" + safeClientId(deviceCode);
            this.sendTimestamps = new long[1024];
        }

        @Override
        public void run() {
            try {
                connect();
                startLatch.countDown();

                // 按固定频率发布
                long periodMicros = TimeUnit.SECONDS.toMicros(1) / ratePerSecond;
                long initialDelay = random.nextInt(200); // 0-200ms 错峰启动

                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "stress-" + deviceCode);
                    t.setDaemon(true);
                    return t;
                });

                publishTask = scheduler.scheduleAtFixedRate(
                        this::publishOne,
                        initialDelay,
                        periodMicros,
                        TimeUnit.MICROSECONDS);

                // 等待停止信号
                while (!stopping && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                System.err.println("[worker " + deviceCode + "] error: " + e.getMessage());
                startLatch.countDown();
            } finally {
                cleanup();
            }
        }

        private void connect() throws MqttException {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setKeepAliveInterval(30);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(true);
            options.setMaxReconnectDelay(3000);

            String clientId = "stress-" + deviceCode + "-" + System.currentTimeMillis();
            client = new MqttClient(broker, clientId,
                    new MqttDefaultFilePersistence(persistenceDir));
            client.setCallback(this);
            client.connect(options);

            if (verbose) {
                System.out.println("[connect] " + deviceCode + " connected to " + broker);
            }
        }

        /**
         * 发布一条遥测消息。每 20 条发送一条重复时间戳用于幂等测试。
         */
        private void publishOne() {
            if (stopping || client == null || !client.isConnected()) {
                return;
            }
            try {
                long sendStart = System.nanoTime();
                long seq = deviceSent.incrementAndGet();
                totalSent.incrementAndGet();

                // 每 20 条发一次重复时间戳（模拟 QoS1 重复投递 / 幂等键碰撞）
                boolean isDup = seq % 20 == 0;
                String ts;
                if (isDup) {
                    // 用上一条的 ts（模拟重复投递）
                    long prevSeq = seq - 1;
                    int idx = (int) (prevSeq % sendTimestamps.length);
                    ts = formatTs(Instant.ofEpochMilli(sendTimestamps[idx]));
                    dupSent.incrementAndGet();
                } else {
                    ts = formatTs(Instant.now());
                    sendTimestamps[(int) (seq % sendTimestamps.length)] =
                            Instant.now().toEpochMilli();
                }

                String payload = buildPayload(ts);
                MqttMessage message = new MqttMessage(
                        payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                message.setRetained(false);

                String topic = "plc/" + SITE_CODE + "/" + deviceCode + "/telemetry";
                client.publish(topic, message);

                if (verbose && seq % 50 == 0) {
                    System.out.println("[pub] " + deviceCode + " #" + seq
                            + " topic=" + topic + " qos=1" + (isDup ? " DUP" : ""));
                }
            } catch (MqttException e) {
                totalFailed.incrementAndGet();
                if (verbose) {
                    System.err.println("[pub-fail] " + deviceCode + ": " + e.getMessage());
                }
            }
        }

        // ===== MqttCallback =====

        @Override
        public void connectionLost(Throwable cause) {
            if (!stopping) {
                System.err.println("[conn-lost] " + deviceCode + ": "
                        + (cause == null ? "unknown" : cause.getMessage()));
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            // 压测只做 publish，不订阅
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            long cSeq = confirmSeq.incrementAndGet();
            totalConfirmed.incrementAndGet();
            deviceConfirmed.incrementAndGet();

            // 采样延迟（每 10 条采一次，避免 ArrayList 膨胀）
            if (cSeq % 10 == 0) {
                try {
                    int idx = (int) (cSeq % sendTimestamps.length);
                    long sendMs = sendTimestamps[idx];
                    if (sendMs > 0) {
                        long latency = System.currentTimeMillis() - sendMs;
                        if (latency >= 0 && latency < 60000) {
                            latencySamples.add(latency);
                        }
                    }
                } catch (Exception ignored) {
                    // best effort
                }
            }

            // 重复消息确认：发送时每 20 条标记为 dup，确认时同步推断
            if (cSeq % 20 == 0) {
                dupConfirmed.incrementAndGet();
            }
        }

        void stop() {
            stopping = true;
        }

        private void cleanup() {
            stopping = true;
            if (publishTask != null) {
                publishTask.cancel(false);
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect(2000);
                    }
                } catch (MqttException ignored) {
                    // best effort
                }
                try {
                    client.close();
                } catch (MqttException ignored) {
                    // best effort
                }
            }
            deleteDirectory(new File(persistenceDir));

            if (verbose) {
                System.out.println("[stop] " + deviceCode
                        + " sent=" + deviceSent.get()
                        + " confirmed=" + deviceConfirmed.get());
            }
        }

        private String buildPayload(String ts) {
            // 模拟 Day 95 契约：current / windingTemp / pressure / speed
            double current = 10 + random.nextDouble() * 8;       // 10~18 A
            double temp = 25 + random.nextDouble() * 20;          // 25~45 °C
            double pressure = 95 + random.nextDouble() * 20;      // 95~115 kPa
            double speed = 1400 + random.nextDouble() * 200;     // 1400~1600 RPM

            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            sb.append("\"deviceCode\":\"").append(deviceCode).append('"');
            sb.append(",\"siteCode\":\"").append(SITE_CODE).append('"');
            sb.append(",\"ts\":\"").append(ts).append('"');
            sb.append(",\"version\":\"").append(PAYLOAD_VERSION).append('"');
            sb.append(",\"current\":").append(round2(current));
            sb.append(",\"windingTemp\":").append(round2(temp));
            sb.append(",\"pressure\":").append(round2(pressure));
            sb.append(",\"speed\":").append(round2(speed));
            sb.append(",\"status\":\"running\"");
            sb.append('}');
            return sb.toString();
        }
    }

    private static String formatTs(Instant instant) {
        return TS_FORMAT.format(instant);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String safeClientId(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
        }
        if (!dir.delete()) {
            dir.deleteOnExit();
        }
    }
}
