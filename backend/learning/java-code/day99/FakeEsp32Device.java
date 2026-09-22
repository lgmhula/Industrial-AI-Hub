package day99;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Day 99：模拟 ESP32（软件链路验证，ADR 0035）。
 *
 * <p>两种模式：
 * <ul>
 *   <li>默认（publish）：以 {@code esp32-dht-001} 身份每 2s 发布一条 telemetry，湿度序列
 *       {@code 55,55,96,96,96,96,55,55}——中间连续超 90% 触发 OVER_HUMIDITY + 后端
 *       RELAY_ON 下行；同时订阅自己的 command Topic 打印收到的命令（模拟继电器执行）。</li>
 *   <li>{@code watch}：只订阅 {@code plc/+/+/command} 打印所有下行命令（P3 真实硬件演示时，
 *       可在另一终端观察后端对真实 ESP32 的下行）。</li>
 * </ul>
 * </p>
 *
 * <p>编译运行（paho 1.2.5 在本地 ~/.m2）：</p>
 * <pre>
 * PAHO=~/.m2/repository/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar
 * javac -cp "$PAHO" -d /tmp/day99 learning/java-code/day99/FakeEsp32Device.java
 * java -cp "$PAHO:/tmp/day99" day99.FakeEsp32Device            # publish 序列
 * java -cp "$PAHO:/tmp/day99" day99.FakeEsp32Device watch      # 监视下行
 * </pre>
 *
 * @author AI 助手
 * @since 2026-09-06
 */
public final class FakeEsp32Device {

    private static final String BROKER = "tcp://127.0.0.1:1883";
    private static final String SITE = "PLANT_A";
    private static final String DEVICE = "esp32-dht-001";
    private static final String TELEMETRY_TOPIC =
            "plc/" + SITE + "/" + DEVICE + "/telemetry";
    private static final String COMMAND_TOPIC =
            "plc/" + SITE + "/" + DEVICE + "/command";
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** 湿度序列：中间 4 条连续 > 90%，验证报警 + 5s 节流下行。 */
    private static final double[] HUMIDITY_SEQ = {55.0, 55.0, 96.0, 96.0, 96.0, 96.0, 55.0, 55.0};
    private static final long INTERVAL_MS = 2000;

    private FakeEsp32Device() {
    }

    public static void main(String[] args) throws Exception {
        boolean watch = args.length > 0 && "watch".equalsIgnoreCase(args[0]);
        if (watch) {
            watchCommands();
        } else {
            publishSequence();
        }
    }

    /** publish 模式：模拟 ESP32 温湿度遥测 + 本地打印下行命令（扮演继电器执行）。 */
    private static void publishSequence() throws MqttException, InterruptedException {
        MqttClient client = connect(DEVICE + "-fake", () -> { });
        client.subscribe(COMMAND_TOPIC, 1);
        System.out.println("[FakeESP32] 发布 telemetry: " + TELEMETRY_TOPIC);
        for (int i = 0; i < HUMIDITY_SEQ.length; i++) {
            double hum = HUMIDITY_SEQ[i];
            String ts = OffsetDateTime.now(ZoneOffset.ofHours(8)).format(TS);
            String payload = "{\"deviceCode\":\"" + DEVICE + "\",\"siteCode\":\"" + SITE
                    + "\",\"ts\":\"" + ts + "\",\"temperature\":28.5,\"humidity\":" + hum + "}";
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            client.publish(TELEMETRY_TOPIC, msg);
            System.out.println("[FakeESP32] #" + (i + 1) + " humidity=" + hum
                    + " published (qos1)");
            Thread.sleep(INTERVAL_MS);
        }
        System.out.println("[FakeESP32] 序列结束，继续监听 command 10s（观察节流）…");
        Thread.sleep(10_000);
        client.disconnect(1000);
        client.close();
    }

    /** watch 模式：打印后端对任何设备的下行 command（P3 配合真实 ESP32）。 */
    private static void watchCommands() throws MqttException, InterruptedException {
        MqttClient client = connect("day99-watch", () -> { });
        client.subscribe("plc/+/+/command", 1);
        System.out.println("[Watcher] 监听 plc/+/+/command，Ctrl+C 退出");
        Thread.currentThread().join();
    }

    private static MqttClient connect(String clientId, Runnable onMessage) throws MqttException {
        MqttClient client = new MqttClient(BROKER, clientId, new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("[MQTT] connectionLost: " + cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println("[MQTT↓] " + topic + "  →  " + payload);
                onMessage.run();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // no-op
            }
        });
        client.connect(opts);
        return client;
    }
}
