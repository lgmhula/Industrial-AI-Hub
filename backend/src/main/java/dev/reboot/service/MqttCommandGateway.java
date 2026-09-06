package dev.reboot.service;

/**
 * MQTT 下行发布端口（Day 99，ADR 0035）。
 *
 * <p>由 {@code MqttConfig.MqttLifecycle} 在 {@code mqtt.enabled=true} 且连接就绪后
 * 通过 {@link MqttDeviceDataIngestService#setCommandGateway} 运行时注入（避免
 * Lifecycle → Ingest → Gateway → Lifecycle 的构造期循环依赖）。MQTT 未启用 /
 * 连接未就绪 / 已停止时为 null，调用方必须做空安全降级。</p>
 *
 * @author AI 助手
 * @since 2026-09-06
 */
public interface MqttCommandGateway {

    /**
     * 向 Broker 发布一条下行消息（如执行器 command）。
     *
     * @param topic   完整 Topic，如 {@code plc/PLANT_A/esp32-dht-001/command}
     * @param payload UTF-8 JSON 字符串
     * @param qos     0/1/2
     * @return true=已交给 Broker（QoS 1 仅表示已投递到 Broker，不保证设备收到）；
     *         false=连接未就绪 / 参数非法 / 发布异常
     */
    boolean publish(String topic, String payload, int qos);
}
