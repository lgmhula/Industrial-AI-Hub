package code.day95;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Day 95 — Java 模拟 PLC：寄存器区模型 + 定时 MQTT 发布。
 *
 * <p>一台模拟设备维护 4 类 Modbus 数据区（Day 92 点位表）：
 * <ol>
 *   <li>Coil 线圈：运行命令 / 继电器等可写开关量；</li>
 *   <li>Discrete Input 离散输入：急停 / 门磁等只读开关量；</li>
 *   <li>Input Register 输入寄存器：温度 / 压力 / 电流等模拟量，使用 scale/offset 换算工程值；</li>
 *   <li>Holding Register 保持寄存器：额定值 / 报警阈值等可读写参数。</li>
 * </ol>
 * 模拟数据每 1~5 秒经 EMQX 发布一次：
 * <ul>
 *   <li>{@code plc/{siteCode}/{deviceCode}/telemetry}（QoS 1，不保留）——遥测与寄存器快照；</li>
 *   <li>{@code plc/{siteCode}/{deviceCode}/status}（QoS 1，保留）——在线状态。</li>
 * </ul>
 * 工程值与寄存器原始值的换算语义与 Day 92 笔记一致：{@code physical = raw * scale + offset}。
 * 顶层点值直接使用工程单位，与项目 {@code device_data.data_type / unit} 对齐（Day 96 消费）。
 *
 * @author AI 助手
 * @since 2026-09-05 (Day 95)
 */
public class PlcSimulator {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Shanghai"));

    /** 遥测间隔范围（秒），模拟 PLC 的扫描 / 上报节奏。 */
    static final int MIN_INTERVAL_SECONDS = 1;
    static final int MAX_INTERVAL_SECONDS = 5;

    /** 默认一次运行秒数；Day 96 联调时可传 0 持续运行。 */
    static final int DEFAULT_RUN_SECONDS = 45;

    private static final String PAYLOAD_VERSION = "1.0";

    private final String broker;
    private final String siteCode;
    private final String deviceCode;
    private final long runSeconds;
    private final boolean verbose;
    private final Random random;
    private final List<Point> points;
    private final List<Point> setpoints;
    private final String persistenceDir;

    private ScheduledExecutorService scheduler;
    private MqttClient client;
    private ScheduledFuture<?> nextPublish;
    private final java.util.concurrent.CompletableFuture<Void> runDone =
            new java.util.concurrent.CompletableFuture<>();
    private volatile boolean stopping;
    private long startNanos;
    private int cycle;

    /**
     * @param broker      EMQX 地址，例如 {@code tcp://localhost:1883}
     * @param siteCode    站点编码（对齐 seed：PLANT_A / PLANT_B / DEFAULT）
     * @param deviceCode  设备编码（建议显式标注 SIM，避免与真实设备混淆）
     * @param runSeconds  运行秒数；小于等于 0 表示持续运行
     * @param verbose     是否打印每条发布消息
     */
    public PlcSimulator(String broker, String siteCode, String deviceCode,
                        long runSeconds, boolean verbose) {
        this.broker = broker;
        this.siteCode = siteCode;
        this.deviceCode = deviceCode;
        this.runSeconds = runSeconds;
        this.verbose = verbose;
        this.random = new Random(deviceCode.hashCode());
        this.points = new ArrayList<>();
        this.setpoints = new ArrayList<>();
        this.persistenceDir = System.getProperty("java.io.tmpdir")
                + File.separator + "paho-" + safeClientId(deviceCode);
    }

    /**
     * 启动模拟器。连接 EMQX 后立即发布第一条遥测，之后按 1~5 秒随机间隔循环。
     *
     * @return 运行完成的 CompletableFuture（异常时包装为 MqttException/IllegalStateException）
     */
    public java.util.concurrent.CompletableFuture<Void> start() {
        try {
            if (client != null || scheduler != null) {
                throw new IllegalStateException("simulator already started: " + deviceCode);
            }
            buildMotorCabinetProfile();
            scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "plc-sim-" + deviceCode);
                t.setDaemon(true);
                return t;
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(true);
            options.setMaxReconnectDelay(5000);
            options.setWill(statusTopic(), statusPayload(false, "offline").getBytes(StandardCharsets.UTF_8), 1, true);

            client = new MqttClient(broker, "plc-sim-" + deviceCode + "-" + System.currentTimeMillis(),
                    new MqttDefaultFilePersistence(persistenceDir));
            client.connect(options);
            startNanos = System.nanoTime();
            cycle = 0;
            print("[connect] broker=" + broker + " device=" + siteCode + "/" + deviceCode);

            // 连接成功后立即发布第一条快照，保证 status retained 主题在联调开始时即可见。
            simulateCycle(cycle);
            publishTelemetry();
            publishStatus();
            scheduleNext();
        } catch (Exception e) {
            runDone.completeExceptionally(e);
            shutdown();
        }
        return runDone;
    }

    private void scheduleNext() {
        if (stopping || client == null || !client.isConnected()) {
            return;
        }
        long delaySeconds = MIN_INTERVAL_SECONDS
                + (long) (random.nextDouble() * (MAX_INTERVAL_SECONDS - MIN_INTERVAL_SECONDS + 1));
        nextPublish = scheduler.schedule(this::tick, delaySeconds, TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            if (stopping) {
                return;
            }
            if (elapsedSeconds() >= runSeconds && runSeconds > 0) {
                stop();
                return;
            }
            simulateCycle(++cycle);
            publishTelemetry();
            publishStatus();
            scheduleNext();
        } catch (MqttException e) {
            System.err.println("[plc-sim " + deviceCode + "] publish failed: "
                    + e.getMessage() + " (reason=" + e.getReasonCode() + ")");
            if (!client.isConnected()) {
                runDone.completeExceptionally(e);
                stop();
            } else {
                scheduleNext();
            }
        } catch (Exception e) {
            System.err.println("[plc-sim " + deviceCode + "] tick failed: " + e.getMessage());
            runDone.completeExceptionally(e);
            stop();
        }
    }

    /**
     * 推进一个模拟扫描周期：输入寄存器做有界随机游走（偶尔越过保持寄存器阈值演示报警），
     * 离散输入小概率出现急停 / 过载，线圈状态随运行工况联动。
     */
    private void simulateCycle(int tickNo) {
        for (Point p : points) {
            if (p.kind == Kind.INPUT_REGISTER) {
                if (p.isSpike()) {
                    p.value += p.step;
                } else {
                    if (tickNo % 7 == 0 && random.nextInt(100) < 12) {
                        // spikeUntil 表示“当前这轮 + 后续 2 轮”，结束后由随机游走回落。
                        p.spikeUntil = 3;
                        p.value = p.min + (p.max - p.min) * (0.92 + random.nextDouble() * 0.08);
                    } else {
                        double target = p.baseline + Math.sin(tickNo / 6.0) * p.jitter;
                        p.value += (target - p.value) * 0.25
                                + (random.nextDouble() - 0.5) * p.step * 2;
                    }
                }
                clamp(p);
                if (p.spikeUntil > 0) {
                    p.spikeUntil--;
                }
            } else if (p.kind == Kind.DISCRETE_INPUT) {
                boolean event = tickNo > 0 && tickNo % 9 == 0 && random.nextInt(100) < 10;
                if (event) {
                    p.value = p.min;
                    p.spikeUntil = 2;
                } else if (p.spikeUntil == 0) {
                    p.value = p.max;
                }
                if (p.spikeUntil > 0) {
                    p.spikeUntil--;
                }
            } else if (p.kind == Kind.COIL) {
                boolean estop = discreteValue("estop") == 0;
                boolean overload = discreteValue("thermalOverload") == 0;
                boolean fault = estop || overload;
                if (p.code.equals("motorStart")) {
                    // 模拟启动命令已保持置位；故障联锁清零，故障恢复后重新置位。
                    p.value = fault ? 0 : 1;
                } else if (p.code.equals("motorRun")) {
                    p.value = fault ? 0 : 1;
                } else {
                    p.value = 0;
                }
            }
        }
        for (Point p : setpoints) {
            // 保持寄存器模拟参数/阈值：本轮只读展示，不下发写入（Downlink 预留）。
            p.value = p.baseline;
        }
    }

    private double discreteValue(String code) {
        for (Point p : points) {
            if (p.kind == Kind.DISCRETE_INPUT && p.code.equals(code)) {
                return p.value;
            }
        }
        return 0;
    }

    private void clamp(Point p) {
        if (p.value < p.min) {
            p.value = p.min;
        }
        if (p.value > p.max) {
            p.value = p.max;
        }
    }

    private void publishTelemetry() throws MqttException {
        long epochMs = System.currentTimeMillis();
        String ts = TS_FORMAT.format(Instant.ofEpochMilli(epochMs));
        String payload = telemetryJson(ts);
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(false);
        client.publish(telemetryTopic(), message);
        if (verbose) {
            print("[publish] " + telemetryTopic() + " qos=1 " + payload);
        }
    }

    private void publishStatus() throws MqttException {
        MqttMessage message = new MqttMessage(
                statusPayload(true, computeStatus()).getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(true);
        client.publish(statusTopic(), message);
        if (verbose) {
            print("[status] " + statusTopic() + " retained=" + computeStatus());
        }
    }

    /**
     * 根据点值与保持寄存器阈值计算状态。DAY 96 可先忽略此字段，
     * 状态看板使用 status 主题保留消息。
     */
    private String computeStatus() {
        if (discreteValue("estop") == 0 || discreteValue("thermalOverload") == 0) {
            return "fault";
        }
        for (Point p : points) {
            if (p.kind == Kind.INPUT_REGISTER && p.dataType != null && p.alarmThreshold != null
                    && p.value > p.alarmThreshold) {
                return "alarm";
            }
        }
        Point run = pointByCode("motorRun");
        return run != null && run.value > 0 ? "running" : "standby";
    }

    private Point pointByCode(String code) {
        for (Point p : points) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return null;
    }

    /** 遥测 JSON：顶层为 device_data 可消费的工程值，registerSnapshot 保留 Modbus 原始视图。 */
    String telemetryJson(String ts) {
        JsonBuilder json = new JsonBuilder();
        json.field("deviceCode", deviceCode);
        json.field("siteCode", siteCode);
        json.field("ts", ts);
        json.field("status", computeStatus());
        json.field("version", PAYLOAD_VERSION);
        for (Point p : points) {
            if (p.kind == Kind.INPUT_REGISTER) {
                json.field(p.dataType.toLowerCase(Locale.ROOT), round2(p.value));
            }
        }
        json.startArray("registerSnapshot");
        for (Point p : points) {
            if (p.kind == Kind.INPUT_REGISTER) {
                json.beginObject()
                        .field("area", "inputRegister")
                        .field("address", p.address)
                        .field("pointCode", p.code)
                        .field("raw", rawValue(p))
                        .field("scale", p.scale)
                        .field("offset", p.offset)
                        .field("unit", p.unit)
                        .endObject();
            } else {
                json.beginObject()
                        .field("area", areaLabel(p.kind))
                        .field("address", p.address)
                        .field("pointCode", p.code)
                        .field("value", p.value > 0 ? 1 : 0)
                        .endObject();
            }
        }
        for (Point p : setpoints) {
            json.beginObject()
                    .field("area", "holdingRegister")
                    .field("address", p.address)
                    .field("pointCode", p.code)
                    .field("raw", rawValue(p))
                    .field("scale", p.scale)
                    .field("offset", p.offset)
                    .field("unit", p.unit)
                    .endObject();
        }
        json.endArray();
        return json.build();
    }

    private String statusPayload(boolean online, String status) {
        return new JsonBuilder()
                .field("deviceCode", deviceCode)
                .field("siteCode", siteCode)
                .field("ts", TS_FORMAT.format(Instant.now()))
                .field("online", online)
                .field("status", status)
                .build();
    }

    private long rawValue(Point p) {
        return Math.round(p.value / p.scale);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private long elapsedSeconds() {
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);
    }

    private void print(String line) {
        System.out.println("[" + TS_FORMAT.format(Instant.now()) + "] " + line);
    }

    /**
     * 停止并清理：发布 offline 保留消息、断开连接、删除临时持久化目录。
     */
    public void stop() {
        if (stopping) {
            return;
        }
        stopping = true;
        if (nextPublish != null) {
            nextPublish.cancel(false);
        }
        try {
            if (client != null && client.isConnected()) {
                MqttMessage offline = new MqttMessage(
                        statusPayload(false, "offline").getBytes(StandardCharsets.UTF_8));
                offline.setQos(1);
                offline.setRetained(true);
                client.publish(statusTopic(), offline);
                client.disconnect(2000);
                print("[stop] " + deviceCode + " disconnected, offline retained published");
            }
        } catch (MqttException e) {
            System.err.println("[stop] " + deviceCode + " cleanup failed: " + e.getMessage());
        } finally {
            closeClient();
            deleteDirectory(new File(persistenceDir));
            shutdownScheduler();
            runDone.complete(null);
        }
    }

    private void closeClient() {
        if (client != null) {
            try {
                client.close();
            } catch (MqttException ignored) {
                // best effort cleanup
            }
            client = null;
        }
    }

    private void shutdownScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void shutdown() {
        try {
            stop();
        } catch (Exception ignored) {
            // 启动失败时尽力清理
        }
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

    private static String safeClientId(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String areaLabel(Kind kind) {
        switch (kind) {
            case COIL: return "coil";
            case DISCRETE_INPUT: return "discreteInput";
            case INPUT_REGISTER: return "inputRegister";
            default: return "holdingRegister";
        }
    }

    private String telemetryTopic() {
        return "plc/" + siteCode + "/" + deviceCode + "/telemetry";
    }

    private String statusTopic() {
        return "plc/" + siteCode + "/" + deviceCode + "/status";
    }

    /**
     * Day 92 电机控制柜示例扩展出的默认点位表。
     * PLC 运行态在安全范围游走；按 7 周期小概率尖峰越过阈值，
     * 供 Day 96 联调时演示「MQTT → device_data → 报警规则」链路。
     */
    private void buildMotorCabinetProfile() {
        points.clear();
        setpoints.clear();

        coil(1, "motorStart", "启动命令", 1);
        coil(2, "motorRun", "运行状态", 1);   // 由电机启停 + 故障条件联动

        discrete(10001, "estop", "急停按钮", 1);
        discrete(10002, "thermalOverload", "热过载触点", 1);

        input("current", "电机电流", "CURRENT", "A",
                30001, 0.1, 0, 8.0, 18.0, 12.6, 0.06);
        input("windingTemp", "绕组温度", "TEMPERATURE", "°C",
                30002, 0.1, 0, 18.0, 46.0, 32.5, 0.20);
        input("pressure", "管道压力", "PRESSURE", "kPa",
                30003, 0.1, 0, 90.0, 116.0, 102.5, 0.25);
        input("speed", "电机转速", "SPEED", "RPM",
                30004, 1, 0, 0.0, 3400.0, 1480.0, 4.0);

        // Holding Register：项目可读的阈值/参数（保持寄存器 = 可读写的“设定值区”）
        setpoint("ratedCurrent", "额定电流", "CURRENT", "A",
                40001, 0.1, 0, 15.0, null);
        setpoint("tempAlarm", "温度报警阈值", "TEMPERATURE", "°C",
                40002, 0.1, 0, 40.0, 40.0);
        setpoint("pressureHiAlarm", "压力上限", "PRESSURE", "kPa",
                40003, 0.1, 0, 110.0, 110.0);
        setpoint("speedHiAlarm", "转速上限", "SPEED", "RPM",
                40004, 1, 0, 3000.0, 3000.0);

        // 输入寄存器报警阈值与项目 AlarmRuleConfig 对齐（温度 40 / 压力 110 / 转速 3000）。
        for (Point p : points) {
            if (p.dataType != null) {
                if ("TEMPERATURE".equals(p.dataType)) {
                    p.alarmThreshold = setpointByName("tempAlarm").value;
                } else if ("PRESSURE".equals(p.dataType)) {
                    p.alarmThreshold = setpointByName("pressureHiAlarm").value;
                } else if ("SPEED".equals(p.dataType)) {
                    p.alarmThreshold = setpointByName("speedHiAlarm").value;
                }
            }
        }
    }

    private Point coil(int address, String code, String name, double initial) {
        Point p = new Point(Kind.COIL, code, name, null, null,
                address, 1, 0, 0, 1, initial, 0, 0, 0);
        points.add(p);
        return p;
    }

    private void discrete(int address, String code, String name, double normal) {
        Point p = new Point(Kind.DISCRETE_INPUT, code, name, null, null,
                address, 1, 0, 0, 1, normal, 0, 0, 0);
        points.add(p);
    }

    private void input(String code, String name, String dataType, String unit,
                       int address, double scale, double offset,
                       double min, double max, double baseline, double jitter) {
        points.add(new Point(Kind.INPUT_REGISTER, code, name, dataType, unit,
                address, scale, offset, min, max, baseline, jitter, 0.25, 0));
    }

    private void setpoint(String code, String name, String dataType, String unit,
                          int address, double scale, double offset,
                          double value, Double alarmThreshold) {
        Point p = new Point(Kind.HOLDING_REGISTER, code, name, dataType, unit,
                address, scale, offset, value, value, value, 0, 0, 0);
        p.alarmThreshold = alarmThreshold;
        setpoints.add(p);
    }

    private Point setpointByName(String code) {
        for (Point p : setpoints) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return null;
    }

    /** 模拟点位（Registers 全为整型视图：工程值 = raw * scale + offset）。 */
    static class Point {
        final Kind kind;
        final String code;
        final String name;
        final String dataType;
        final String unit;
        final int address;
        final double scale;
        final double offset;
        final double min;
        final double max;
        final double baseline;
        final double step;
        final double jitter;
        double value;
        int spikeUntil;
        Double alarmThreshold;

        Point(Kind kind, String code, String name, String dataType, String unit,
              int address, double scale, double offset,
              double min, double max, double baseline, double jitter,
              double step, int spikeUntil) {
            this.kind = kind;
            this.code = code;
            this.name = name;
            this.dataType = dataType;
            this.unit = unit;
            this.address = address;
            this.scale = scale;
            this.offset = offset;
            this.min = min;
            this.max = max;
            this.baseline = baseline;
            this.jitter = jitter;
            this.step = step;
            this.value = baseline;
            this.spikeUntil = spikeUntil;
        }

        boolean isSpike() {
            return spikeUntil > 0;
        }
    }

    enum Kind {
        COIL,
        DISCRETE_INPUT,
        INPUT_REGISTER,
        HOLDING_REGISTER
    }

    /** 极简 JSON 序列化器：保持字段顺序，便于学习代码阅读与 Day 96 定稿解析契约。 */
    static final class JsonBuilder {
        private final StringBuilder sb = new StringBuilder(512);
        private boolean first = true;
        private boolean array;

        JsonBuilder field(String key, String value) {
            separator();
            sb.append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"');
            return this;
        }

        JsonBuilder field(String key, boolean value) {
            separator();
            sb.append('"').append(escape(key)).append("\":").append(value);
            return this;
        }

        JsonBuilder field(String key, long value) {
            separator();
            sb.append('"').append(escape(key)).append("\":").append(value);
            return this;
        }

        JsonBuilder field(String key, double value) {
            separator();
            sb.append('"').append(escape(key)).append("\":");
            if (Double.isFinite(value)) {
                sb.append(value);
            } else {
                sb.append(0);
            }
            return this;
        }

        JsonBuilder beginObject() {
            if (!array) {
                throw new IllegalStateException("beginObject only allowed inside array");
            }
            separator();
            sb.append('{');
            first = true;
            return this;
        }

        JsonBuilder endObject() {
            sb.append('}');
            first = false;
            return this;
        }

        JsonBuilder startArray(String key) {
            separator();
            sb.append('"').append(escape(key)).append("\":[");
            array = true;
            first = true;
            return this;
        }

        JsonBuilder endArray() {
            sb.append(']');
            array = false;
            first = false;
            return this;
        }

        private void separator() {
            if (!first) {
                sb.append(',');
            }
            first = false;
        }

        private static String escape(String s) {
            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                }
            }
            return out.toString();
        }

        String build() {
            if (sb.length() == 0) {
                return "{}";
            }
            return "{" + sb + "}";
        }
    }
}
