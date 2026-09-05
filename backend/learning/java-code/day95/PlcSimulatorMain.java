package code.day95;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Day 95 — 启动一个或多个 Java 模拟 PLC。
 *
 * <p>默认启动一台 PLC 点位模拟器 {@code plc-sim-001}（站点 PLANT_A）。
 * 传参可连续追加多个设备：
 *
 * <pre>
 *   java code.day95.PlcSimulatorMain                                  # PLC-SIM-001 × 1
 *   java code.day95.PlcSimulatorMain tcp://localhost:1883 30          # 默认设备跑 30 秒
 *   java code.day95.PlcSimulatorMain 60 PLC-SIM-001 PLC-SIM-002       # 多台默认站点设备
 *   java code.day95.PlcSimulatorMain 90 PLANT_A/PLC-SIM-001 PLANT_B/PLC-SIM-002
 * </pre>
 *
 * <p>未显式传设备编码时默认 {@code PLC-SIM-001}，站点为 {@code PLANT_A}；
 * 一个进程可管理多个 Paho client，每个设备独立线程按 1~5s 发布。
 */
public final class PlcSimulatorMain {

    private static final String DEFAULT_BROKER = "tcp://localhost:1883";
    private static final String DEFAULT_SITE = "PLANT_A";
    private static final String DEFAULT_DEVICE = "PLC-SIM-001";

    private PlcSimulatorMain() {
    }

    public static void main(String[] args) throws Exception {
        String broker = DEFAULT_BROKER;
        long seconds = PlcSimulator.DEFAULT_RUN_SECONDS;
        List<String[]> devices = new ArrayList<>();
        devices.add(new String[]{DEFAULT_SITE, DEFAULT_DEVICE});

        for (String arg : args) {
            if (arg.startsWith("tcp://") || arg.startsWith("ssl://") || arg.startsWith("ws://")) {
                broker = arg;
            } else if (arg.matches("\\d+")) {
                seconds = Long.parseLong(arg);
            } else if (arg.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_-]+")) {
                String[] pair = arg.split("/", 2);
                devices.add(new String[]{pair[0], pair[1]});
            } else {
                if (devices.size() == 1 && DEFAULT_DEVICE.equals(devices.get(0)[1])) {
                    devices.set(0, new String[]{DEFAULT_SITE, arg});
                } else {
                    devices.add(new String[]{DEFAULT_SITE, arg});
                }
            }
        }

        List<PlcSimulator> sims = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        System.out.println("[main] broker=" + broker + " seconds=" + seconds
                + " devices=" + devices.size());
        for (String[] dev : devices) {
            PlcSimulator sim = new PlcSimulator(broker, dev[0], dev[1], seconds, true);
            sims.add(sim);
            futures.add(sim.start());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (PlcSimulator sim : sims) {
                sim.stop();
            }
        }, "plc-sim-shutdown"));

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            System.err.println("[main] simulator error: " + e.getMessage());
            for (PlcSimulator sim : sims) {
                sim.stop();
            }
            System.exit(1);
        }

        if (seconds > 0) {
            System.out.println("[main] run finished after " + seconds + "s");
        }
    }
}
