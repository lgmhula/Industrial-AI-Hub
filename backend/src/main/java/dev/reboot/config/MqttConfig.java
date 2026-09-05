package dev.reboot.config;

import dev.reboot.service.MqttDeviceDataIngestService;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * MQTT Listener 生命周期配置（Day 96，ADR 0034）。
 *
 * <p>仅在 {@code mqtt.enabled=true} 且非 test profile 时创建 Paho Client，
 * 启动后连接 EMQX 并订阅 {@code plc/+/+/telemetry}；test profile 下不创建任何
 * Socket，保证 343 个既有单元测试不依赖外部 Broker。</p>
 *
 * @author AI 助手
 * @since 2026-09-05
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    public MqttConfig(MqttProperties properties) {
        if (!properties.isEnabled()) {
            log.info("MQTT Listener disabled (mqtt.enabled=false)，跳过 EMQX 连接与订阅");
        }
    }

    /**
     * MQTT 生命周期 Bean：SmartLifecycle 启动阶段最后建连，关闭阶段最先断开。
     */
    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
    public MqttLifecycle mqttLifecycle(MqttProperties properties,
                                       MqttDeviceDataIngestService ingestService) {
        return new MqttLifecycle(properties, ingestService);
    }

    static final class MqttLifecycle implements SmartLifecycle, MqttCallback {

        private static final Logger lifecycleLog = LoggerFactory.getLogger(MqttLifecycle.class);
        private static final String TCP_PREFIX = "tcp://";

        private final MqttProperties properties;
        private final MqttDeviceDataIngestService ingestService;

        private volatile MqttClient client;
        private volatile boolean running;

        MqttLifecycle(MqttProperties properties, MqttDeviceDataIngestService ingestService) {
            this.properties = properties;
            this.ingestService = ingestService;
        }

        @Override
        public synchronized void start() {
            if (running) {
                return;
            }
            MqttClient mqttClient = null;
            try {
                MqttDefaultFilePersistence persistence =
                        new MqttDefaultFilePersistence(persistenceDirectory());
                mqttClient = new MqttClient(
                        serverUri(properties), properties.getClientId(), persistence);
                mqttClient.setCallback(this);

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(properties.isCleanSession());
                options.setKeepAliveInterval(properties.getKeepAliveSeconds());
                options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
                options.setAutomaticReconnect(true);
                options.setMaxReconnectDelay(5000);

                mqttClient.connect(options);
                mqttClient.subscribe(properties.getTopicFilter(), properties.getQos());
                this.client = mqttClient;
                this.running = true;
                lifecycleLog.info("MQTT Listener connected: broker={} topic={} qos={}",
                        serverUri(properties), properties.getTopicFilter(), properties.getQos());
            } catch (MqttException ex) {
                if (mqttClient != null) {
                    try {
                        mqttClient.close();
                    } catch (MqttException ignored) {
                        // best effort cleanup
                    }
                }
                closeQuietly();
                running = false;
                lifecycleLog.error("MQTT Listener connect/subscribe failed: broker={} topic={}",
                        serverUri(properties), properties.getTopicFilter(), ex);
                throw new IllegalStateException("MQTT Listener 启动失败（请检查 mqtt.enabled/host/port）", ex);
            }
        }

        @Override
        public synchronized void stop() {
            running = false;
            if (client == null) {
                return;
            }
            try {
                if (client.isConnected()) {
                    client.disconnect(2000);
                }
            } catch (MqttException ex) {
                lifecycleLog.warn("MQTT Listener disconnect failed: {}", ex.getMessage());
            } finally {
                closeQuietly();
                deleteDirectory(new File(persistenceDirectory()));
            }
            lifecycleLog.info("MQTT Listener stopped");
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            // 最后启动（在 Redis/RabbitMQ 等基础 Bean 之后建连）
            return Integer.MAX_VALUE;
        }

        @Override
        public void connectionLost(Throwable cause) {
            // Paho automaticReconnect=true：日志记录后由 Paho 自动重连
            lifecycleLog.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.toString());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                boolean persisted = ingestService.ingest(topic, payload);
                lifecycleLog.debug("MQTT message handled topic={} persisted={} payloadSize={}",
                        topic, persisted, payload.length());
            } catch (RuntimeException ex) {
                // MqttCallback 实现禁止抛异常：任何异常只记录，避免 Paho 断开
                lifecycleLog.error("MQTT message ingest failed topic={}", topic, ex);
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            try {
                MqttMessage acked = token == null ? null : token.getMessage();
                lifecycleLog.debug("MQTT delivery complete: {}",
                        acked == null ? "no-message" : "payloadSize=" + acked.getPayload().length);
            } catch (MqttException ex) {
                lifecycleLog.debug("MQTT delivery complete token read failed: {}", ex.getMessage());
            }
        }

        private void closeQuietly() {
            if (client != null) {
                try {
                    client.close();
                } catch (MqttException ignored) {
                    // best effort cleanup
                }
                client = null;
            }
        }

        private String serverUri(MqttProperties p) {
            if (p.getHost() == null || p.getHost().contains("://")) {
                return p.getHost();
            }
            return TCP_PREFIX + p.getHost() + ":" + p.getPort();
        }

        private String persistenceDirectory() {
            if (properties.getPersistenceDir() != null
                    && !properties.getPersistenceDir().isBlank()) {
                return properties.getPersistenceDir();
            }
            return System.getProperty("java.io.tmpdir")
                    + File.separator + "paho-" + safeClientId(properties.getClientId());
        }

        private static String safeClientId(String clientId) {
            return clientId.replaceAll("[^A-Za-z0-9_-]", "_");
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
}
