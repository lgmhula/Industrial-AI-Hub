# Day 97 — 多设备并发 MQTT 压力测试（Phase 5 Week 15 第 6 天）

> **日期**：2026-09-05
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 6 天
> **分支**：`feat/day97-multi-device-stress`
> **配套笔记**：[mqtt-learning-notes.md](../../docs/notes/mqtt-learning-notes.md) §14
> **验收结果**：✅ **GO**（20 台 × 10 msg/s = 201 msg/s 吞吐，3020 条 100% 确认 0 失败，延迟 max 5ms + 后端 355/355 全绿）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 压测代码 | `backend/learning/java-code/day97/MultiDeviceStressTest.java` | N 台设备并发 Paho Client 向 EMQX publish QoS 1 遥测 + 聚合吞吐/延迟/幂等指标 + 汇总报告 |
| 学习笔记 | `docs/notes/mqtt-learning-notes.md` §14 | 压测设计 + 实测结果 + 关键观察 + 局限与后续 |
| 日日志 | `backend/DAILY/Day97.md` | 本文件 |

## 二、压测设计

### 2.1 架构

```text
MultiDeviceStressTest.main()
  → 创建 N 个 DeviceWorker（各自独立 Paho Client + QoS 1）
  → CountDownLatch 等待全部连接 EMQX
  → scheduleAtFixedRate 按 rate msg/s 并发 publish
  → 每 20 条发 1 条重复时间戳（模拟 QoS 1 重复投递 → 幂等碰撞）
  → deliveryComplete 回调用 confirmSeq 确认计数器采集延迟
  → duration 后 stop 所有 worker + 汇总报告
```

### 2.2 指标采集

| 指标 | 采集方式 |
|------|---------|
| 发送总数 | `AtomicLong totalSent`（publish 前自增） |
| 确认总数 | `AtomicLong totalConfirmed`（deliveryComplete 自增） |
| 失败总数 | `AtomicLong totalFailed`（MqttException 时自增） |
| 延迟采样 | `sendTimestamps` 环形缓冲 + `confirmSeq` 索引（每 10 条采样一次） |
| 幂等碰撞 | 每 20 条标记 dup，dupSent/dupConfirmed 分别计数 |

## 三、实测结果

### 3.1 小规模验证（3 台 × 2 msg/s × 10s）

| 指标 | 值 |
|------|----|
| 发送 / 确认 | 60 / 60 (100%) |
| 失败 | 0 |
| 吞吐 | 6.0 msg/s |
| 延迟 avg / max | 2ms / 2ms |
| 幂等重复 | 3 sent / 3 confirmed |

### 3.2 正式压测（20 台 × 10 msg/s × 15s）

| 指标 | 值 |
|------|----|
| 发送 / 确认 | 3020 / 3020 (100%) |
| 失败 | 0 |
| 吞吐 | 201.3 msg/s |
| 延迟 avg | 2.26ms |
| 延迟 p50 / p95 / p99 | 2 / 4 / 5ms |
| 延迟 max | 5ms |
| 幂等重复 | 140 sent / 140 confirmed |

## 四、关键观察

1. **EMQX 5.8.9 单机承载 200+ msg/s 无压力**：延迟稳定 2-5ms，零丢包。
2. **Paho deliveryComplete 高频可靠**：3020 条全确认，Day 94 的 null token 坑未复现。
3. **QoS 1 顺序性**：同 client 的 deliveryComplete 按发送顺序回调，确认计数器可索引发送时间环采样延迟。
4. **单实例 Paho 回调非瓶颈**：200 msg/s 同步入库可接受；500+ msg/s 时考虑有界队列或 `MqttAsyncClient`。
5. **幂等碰撞设计**：5% 重复时间戳消息已发送，后端 Redis SETNX 去重效果需在 Day 99 全链路联调时从 `device_data` 行数验证。

## 五、压测局限与后续

| 局限 | 后续改进 |
|------|---------|
| `PLC-STRESS-NNN` 未在 DB 注册，后端会跳过 | Day 99 联调前 `scripts/seed-dev.sh` 预置压测设备 |
| 幂等去重未在数据层验证 | Day 99 启动 backend + `MQTT_ENABLED=true`，压测后查 `device_data` 行数 |
| 只覆盖 publish 侧 | Day 99 全链路联调时由后端日志观察入库速率与报警触发 |

## 六、验证

- EMQX 5.8.9 容器 healthy（Up 25 hours）
- 压测代码编译通过，两次运行（3 台 + 20 台）均 PASS
- `./mvnw test` → **355/355 全绿 BUILD SUCCESS**（本日仅新增 learning 代码，不改生产代码）

## 七、明日计划（Day 98）

1. **Week 15 周复盘**：`backend/REVIEW/Week15.md`，覆盖 Day 92-97（PLC/Modbus 概念 → MQTT 协议 → Paho 客户端 → 模拟 PLC → 生产 Listener → 多设备压测）6 天总结；
2. **PLC/MQTT 笔记整理**：`plc-modbus-learning-notes.md` + `mqtt-learning-notes.md` 通读修订；
3. 后端测试与前端 build 继续全绿；
4. 创建 Day98.md 日志。

---

> Day 97 结束。Phase 5 Week 15 第 6 天：多设备并发压测通过，EMQX 200+ msg/s 零丢包，下一步 Day 98 周复盘。
> 维护者：AI 助手 + hula0710
