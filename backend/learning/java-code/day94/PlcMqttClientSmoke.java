package code.day94;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 94 — Eclipse Paho (MQTT 3.1.1) 客户端冒烟测试。
 *
 * <h3>目标</h3>
 * <ol>
 *   <li>用 Paho 连接本地 EMQX（tcp://localhost:1883）；</li>
 *   <li>订阅 {@code plc/test/dev-smoke/telemetry}，QoS 1；</li>
 *   <li>依次发布 QoS 0 / 1 / 2 三条消息，观察回环收到的实际 QoS（端到端取 min）；</li>
 *   <li>验证 MqttCallback 三回调（connectionLost / messageArrived / deliveryComplete）行为。</li>
 * </ol>
 *
 * <h3>运行方式</h3>
 * <pre>
 *   cd backend
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/day94-cp.txt
 *   javac -encoding UTF-8 -cp "$(cat /tmp/day94-cp.txt)" \
 *         -d /tmp/day94-out learning/java-code/day94/PlcMqttClientSmoke.java
 *   java -cp "/tmp/day94-out:$(cat /tmp/day94-cp.txt)" code.day94.PlcMqttClientSmoke
 *   # 也可指定 broker：java ... code.day94.PlcMqttClientSmoke tcp://localhost:1883
 * </pre>
 *
 * <h3>预期输出</h3>
 * <pre>
 *   [connect] broker=tcp://localhost:1883 clientId=plc-smoke-xxx connected
 *   [subscribe] topic=plc/test/dev-smoke/telemetry qos=1
 *   [publish] qos=0 payload={"deviceCode":"dev-smoke","seq":0,"qos":0}
 *   [recv]    topic=plc/test/dev-smoke/telemetry qos=0 payload=...
 *   [deliveryComplete] qos=0 msgId=...
 *   [publish] qos=1 ...
 *   [recv]    qos=1 ...
 *   [publish] qos=2 ...
 *   [recv]    qos=1   ← 订阅端 QoS 1，端到端=min(2,1)=1
 *   [summary] sent=3 received=3 PASS
 * </pre>
 */
public class PlcMqttClientSmoke {

    private static final String DEFAULT_BROKER = "tcp://localhost:1883";
    private static final String TOPIC = "plc/test/dev-smoke/telemetry";
    private static final String CLIENT_ID_PREFIX = "plc-smoke-";
    private static final int SUBSCRIBE_QOS = 1;

    public static void main(String[] args) {
        String broker = args.length > 0 ? args[0] : DEFAULT_BROKER;
        String clientId = CLIENT_ID_PREFIX + System.currentTimeMillis();

        MqttClient client = null;
        try {
            // 1. 构造客户端：内存持久化（学习场景不需要磁盘队列）
            client = new MqttClient(broker, clientId, new MemoryPersistence());

            // 2. 配置回调
            AtomicInteger received = new AtomicInteger(0);
            CountDownLatch done = new CountDownLatch(3); // QoS 0/1/2 共 3 条
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.printf("[connectionLost] %s%n", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    int n = received.incrementAndGet();
                    System.out.printf("[recv %d] topic=%s qos=%d payload=%s%n",
                            n, topic, message.getQos(),
                            new String(message.getPayload(), StandardCharsets.UTF_8));
                    done.countDown();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 注意：QoS 0 投递完成时 token.getMessage() 可能为 null（Paho 不保留 QoS 0 消息引用），
                    // 需做空判断；QoS 1/2 才会携带消息体。
                    try {
                        MqttMessage m = token.getMessage();
                        if (m != null) {
                            System.out.printf("[deliveryComplete] qos=%d msgId=%d%n",
                                    m.getQos(), token.getMessageId());
                        } else {
                            System.out.printf("[deliveryComplete] qos=0 (no payload ref) msgId=%d%n",
                                    token.getMessageId());
                        }
                    } catch (MqttException e) {
                        System.out.printf("[deliveryComplete] msgId=%d err=%s%n",
                                token.getMessageId(), e.getMessage());
                    }
                }
            });

            // 3. 连接选项：cleanSession=true（无状态遥测）、keepAlive 60s、超时 10s
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setConnectionTimeout(10);
            // EMQX 本地开发允许匿名直连，无需用户名密码（ADR 0033）

            client.connect(options);
            System.out.printf("[connect] broker=%s clientId=%s connected%n", broker, clientId);

            // 4. 订阅（QoS 1）
            client.subscribe(TOPIC, SUBSCRIBE_QOS);
            System.out.printf("[subscribe] topic=%s qos=%d%n", TOPIC, SUBSCRIBE_QOS);

            // 5. 依次发布 QoS 0 / 1 / 2
            int[] qosLevels = {0, 1, 2};
            for (int qos : qosLevels) {
                String payload = String.format(
                        "{\"deviceCode\":\"dev-smoke\",\"ts\":%d,\"qos\":%d,\"seq\":%d}",
                        System.currentTimeMillis(), qos, qos);
                MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                msg.setQos(qos);
                msg.setRetained(false);
                client.publish(TOPIC, msg);
                System.out.printf("[publish] qos=%d payload=%s%n", qos, payload);
            }

            // 6. 等待回环（最多 5 秒）
            boolean allArrived = done.await(5, TimeUnit.SECONDS);
            System.out.printf("[summary] sent=3 received=%d %s%n",
                    received.get(), allArrived ? "PASS" : "FAIL (timeout, check EMQX)");

        } catch (MqttException e) {
            System.err.printf("[MqttException] reasonCode=%d msg=%s%n",
                    e.getReasonCode(), e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[InterruptedException] " + e.getMessage());
            System.exit(1);
        } finally {
            // 7. 清理：断开 + 关闭
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect();
                        System.out.println("[disconnect] done");
                    }
                    client.close();
                } catch (MqttException e) {
                    System.err.println("[cleanup] " + e.getMessage());
                }
            }
        }
    }
}
