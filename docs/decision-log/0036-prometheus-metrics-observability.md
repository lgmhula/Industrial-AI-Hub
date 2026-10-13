# Decision 0036: Prometheus 指标观测 — Actuator + Micrometer Registry + compose 集成（Day 100 系统监控）

| 属性 | 值 |
|------|------|
| **状态** | ✅ 已采纳（Day 100 落地） |
| **决策日期** | 2026-10-13 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | DAILY_ROADMAP Day 100（系统监控：Actuator + Prometheus）/ ADR 0034（MQTT Listener，业务指标载体）/ ADR 0035（RELAY_ON 下行，业务指标载体）/ Infrastructure-Baseline（compose v1.5）/ Day 102 JMeter 压测（观测基线前置） |

## 1. 背景

Day 99 真机闭环跑通后，「传感器 → MQTT → 落库 → 报警 → 下行 → 执行器」全链路
已在生产通道上运行，但系统仍是**黑盒**：落库速率、报警频率、下行节流命中、JVM
状态都只能靠翻日志。DAILY_ROADMAP Day 100 规划「Spring Boot Actuator + Prometheus」，
同时 Day 102 JMeter 压测需要一个可量化观测的基线，否则压测结果无从对照。

指标暴露方案候选：Prometheus 拉取（Actuator + Micrometer Registry）/ push 网关 /
ELK 日志聚合（Day 101 可选项）/ APM 全链路（SkyWalking 等，超纲）。

## 2. 决策

### 2.1 技术选型：Prometheus 拉取模式

- 后端新增 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
  （版本由 Spring Boot 3.5.0 parent 管理，不显式锁版本）；
- 暴露端点收敛为 `health,info,prometheus`（原配置仅 health，合并进既有
  `management:` 段，避免 YAML 重复键——本日踩坑，见 §4）；
- 抓取端点 `/actuator/prometheus`，全局 tag `application=industrial-ai-hub`；
- compose **v1.5** 新增 `prometheus` 服务（`prom/prometheus:v2.55.1`，9090，
  named volume `prometheus-data` 持久化，接入 `industrial-network`，
  `extra_hosts: host.docker.internal:host-gateway` 兼容 Linux）；
- dev 模式后端跑宿主机 → Prometheus 默认抓 `host.docker.internal:8080`；
  容器化部署后在 `prometheus/prometheus.yml` 追加 `backend:8080` 目标。

### 2.2 自定义业务指标（三把尺子对齐 MQTT 主链路）

| 指标（Prometheus 名） | Micrometer 名 | 标签 | 打点位置 | 语义 |
|---|---|---|---|---|
| `mqtt_ingest_messages_total` | `mqtt.ingest.messages` | `device_code` / `outcome=ok\|skipped` | `MqttDeviceDataIngestService.ingest/ingestObject` | 遥测消费速率 + 丢弃比 |
| `mqtt_alarm_triggered_total` | `mqtt.alarm.triggered` | `device_code` / `alarm_type` | `checkAndSendAlarms` 每条报警 | 报警产生频率（按类型） |
| `mqtt_relay_command_total` | `mqtt.relay.command` | `device_code` / `outcome=sent\|failed\|throttled` | `maybeSendRelayCommand` | 下行成功率 + 5s 节流命中 |

打点纪律（与 ADR 0034 降级哲学一致）：`MeterRegistry` 缺失（测试直构）或任何
打点异常 → 降级为不记指标，**绝不阻塞业务链路**（`count()` helper 全量
try/catch 兜底）。

### 2.3 安全边界

- `/actuator/**` 不在 `/api/**` 拦截器链内（AuthInterceptor/Auth 限流均只挂
  `/api/**`），JWT 不覆盖——**dev 可接受，生产部署必须在网络层限制** 9090 与
  8080 的 `/actuator` 访问（compose 内网 or 反代 ACL）；
- 暴露面收敛：仅 `health,info,prometheus`，env/beans/mappings 等敏感端点不开放。

## 3. 备选方案（为什么不是它们）

1. **ELK 日志聚合（Day 101 可选）**：解决「查日志」而非「看指标」；无聚合数值
   类型，速率/比率需日志二次加工；资源开销大（ES 8.17 已 compose 锁定但非为指标
   设计）→ 指标归 Prometheus，日志归 ELK，各司其职；
2. **Pushgateway**：适合批处理任务短生命周期上报；本项目后端常驻，拉取模式更
   简单且自带服务发现/健康检查语义；
3. **APM（SkyWalking/Zipkin）**：Trace 维度超 Day 100 范围，技术栈锁定约束下不
   引入新观测面（§5 技术栈锁定纪律），留 Phase 6。

## 4. 踩坑记录

`application.yml` 已有 `management:` 段（早期 Day 37 只暴露 health），本日直接
**新增第二个 `management:` 键** → SnakeYAML `DuplicateKeyException` →
4 个 @SpringBootTest 上下文加载失败（7 errors）。修复：合并进既有段。
**教训：往 application.yml 加顶级键前先 grep 是否已存在。**

## 5. 后果

- 正向：MQTT 主链路三把业务尺子 + JVM/HTTP 系统指标全量可查（Prometheus UI
  http://localhost:9090）；Day 102 JMeter 压测有观测基线；指标打点降级不伤业务；
- 代价：+2 依赖（parent 管理）、+1 容器常驻（prometheus）、测试 +1
  （`metrics_shouldCountIngestAlarmAndRelayCommand`，SimpleMeterRegistry 断言
  三计数器）；
- 未做（后续）：Grafana 面板、告警规则（Alertmanager）、指标接入前端展示——
  按需另开 Day。

> 维护者：AI 助手 + hula0710
