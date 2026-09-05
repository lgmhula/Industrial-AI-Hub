package dev.reboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT Listener 配置属性（Day 96，ADR 0034）。
 *
 * <p>默认关闭，避免没有 EMQX 的开发环境在应用启动时尝试建连；
 * 启用后由 {@link MqttConfig} 创建 Paho MqttClient 并订阅遥测 Topic。</p>
 *
 * @author AI 助手
 * @since 2026-09-05
 */
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    /** 是否启用 MQTT Listener。 */
    private boolean enabled = false;

    /** Broker 主机名（不含协议前缀，可填写 emqx 服务名）。 */
    private String host = "127.0.0.1";

    /** Broker TCP 端口。 */
    private int port = 1883;

    /** Paho 客户端 ID，同一时间同一 Broker 内不能重复。 */
    private String clientId = "iah-backend-mqtt";

    /** 订阅 Topic 过滤器（默认覆盖所有站点的遥测）。 */
    private String topicFilter = "plc/+/+/telemetry";

    /** 订阅 QoS（0/1/2）。 */
    private int qos = 1;

    /** 是否使用干净会话。 */
    private boolean cleanSession = true;

    /** Keep Alive 间隔（秒）。 */
    private int keepAliveSeconds = 60;

    /** 连接超时（秒）。 */
    private int connectionTimeoutSeconds = 10;

    /** Paho 文件持久化目录；留空自动使用 java.io.tmpdir/paho-iah-backend-mqtt。 */
    private String persistenceDir = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTopicFilter() { return topicFilter; }
    public void setTopicFilter(String topicFilter) { this.topicFilter = topicFilter; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
    public boolean isCleanSession() { return cleanSession; }
    public void setCleanSession(boolean cleanSession) { this.cleanSession = cleanSession; }
    public int getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }
    public String getPersistenceDir() { return persistenceDir; }
    public void setPersistenceDir(String persistenceDir) { this.persistenceDir = persistenceDir; }
}
