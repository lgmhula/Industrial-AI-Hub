# Day 99 — 完整系统联调 + 真实 ESP32 硬件 6 段闭环（Phase 5 Week 16 第 1 天）

> **日期**：2026-09-06（P1 固件/ADR）→ 2026-10-13（P2 接线 + 真机联调收口）
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 16 第 1 天
> **分支**：`feat/day99-hw-closed-loop`（P1，已合并 `31e5c61`）/ `feat/day99-esp32-p2-closed-loop`（P2 本次收口）
> **配套产出**：[ADR 0035](../docs/decision-log/0035-mqtt-relay-command-downlink.md) 下行 command 契约 + [esp32-dht-relay.ino](../../backend/learning/esp32/esp32-dht-relay/esp32-dht-relay.ino) 固件 + [硬件接线图册](../../docs/notes/hardware-wiring-guide/hardware-wiring-guide.html)
> **验收结果**：✅ **GO**（硬件 6 段闭环温度/湿度双分支实测通过；后端基线 359/359 沿用 P1，P2 无生产代码变更）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 固件 | `backend/learning/esp32/esp32-dht-relay/esp32-dht-relay.ino` | ESP32-S3 #1：WiFi→EMQX→telemetry 2s QoS1 + command 订阅（RELAY_ON 吸合 3s 自动复位 + 3s 去抖）+ LWT offline retained；引脚修正 `RELAY_PIN 5→14`（GPIO14 与 5V0/GND 相邻，三线成束；避开 N16R8 PSRAM 35/36/37） |
| 接线图册 | `docs/notes/hardware-wiring-guide/hardware-wiring-guide.html`（含节点 A 总装超大图） | 按实拍板（N16R8 双 Type-C，左排 21 脚 + 右排 22 脚，无 GPIO8）逐脚校准；乐高式分步：识别件 → 面包板半区原理 → 引脚安全地图 → 节点 A/B 六线分步（色标+行列坐标）→ 上电前核对 → 万用表验证 |
| 固件指南 | `backend/learning/esp32/esp32-dht-relay/README.md` | 工具链安装 / 烧录三处必填 / 板型设置（USB CDC On Boot）/ 验证顺序 P2→P3 / 排错速查 |
| 配置修正 | 根目录 `.env`（不入库） | 补 `MQTT_ENABLED=true / MQTT_HOST=127.0.0.1 / MQTT_PORT=1883`——此前本地 dev 缺失，MQTT 监听靠运行时环境变量偶然生效，现回归 SSOT（ADR 0015） |
| 日日志 | `backend/DAILY/Day99.md` | 本文件 |

> P1（2026-09-06 已提交 `d661474`）：ADR 0035 下行 command 契约 + `MqttCommandGateway` + `MqttDeviceDataIngestService` RELAY_ON 节流（Redis 5s）+ ESP32 固件交付，后端 359/359。
> P1.5（09-07 `c08fc68`）：ESP32 硬件零基础学习路线 + 教材清单。

## 二、真机 6 段闭环验证记录（2026-10-13）

**链路**：DHT22 采集 → MQTT 上报（`plc/PLANT_A/esp32-dht-001/telemetry`）→ 后端落库 → 报警规则 → RELAY_ON 下行（`.../command`）→ 继电器吸合 3s 自动复位。

| 时段 | 操作 | 数据证据 | 判定 |
|------|------|---------|------|
| 16:19–17:00 | 静置基线 | 29°C / 52%RH，每 2s 稳定两条落库（TEMPERATURE + HUMIDITY），40 分钟无跳变 | ✅ 链路通、接线正确 |
| 16:26 | 对 DHT22 哈气 | 湿度 52→**100%**，`OVER_HUMIDITY`（severity 1）×9，RELAY_ON ×3（5s 节流窗口），继电器吸合 3s×3 | ✅ 湿度分支 |
| 17:01–17:08 | 吹风机热风 | 温度 29→**45–68°C**，`OVER_TEMP`（severity 2）持续每 2s 一条（共 226 条报警含前序），RELAY_ON 每 5s 一次，继电器连续跳变 | ✅ 温度分支 |
| 17:08 后 | 停止吹风 | 读数回落 30.0°C / 49.7%，报警自动停止，继电器复位 | ✅ 自动恢复 |

累计：`device_data` 3728 行（09-06 P1 冒烟起）；EMQX 双客户端（`esp32-dht-001` + `iah-backend-mqtt`）全程在线；测试报警 226 条已批量标记 resolved（status=2）。

## 三、关键坑位与方法论

1. **热风吹传感器 ≠ 高湿度**：相对湿度随温度升高而降低（物理规律），热风触发的是 **OVER_TEMP** 分支；湿度触发正解是**哈气**（呼气 ~35°C/95%RH 遇室温传感器凝结，RH 直顶 100%）。本案 68°C/10.9%RH 是热干风的**真实读数**，不是坏数据。
2. **坏数据 vs 真实输入的判别顺序**：先看静置基线是否稳定（稳定=接线正确）→ 再判断野值是否有物理来源（吹风机）→ 最后才怀疑接线/固件。本次若先拆线排查反而走弯路。
3. **DHT22 上限 80°C**：实测吹到 68°C 已近极限，长时间近距离热风可能永久损坏传感器。
4. **`.env` SSOT 漂移**：本地 dev 跑后端时 MQTT 三变量缺失，监听靠 IDEA 运行时环境变量偶然生效——违反 ADR 0015，已回归 `.env` 单一来源；提醒：Run Configuration 手填的环境变量应删除。
5. **报警风暴（技术债）**：持续超限期间每 2s 采样即插一条 alarm（无合并/去重），一次吹风 100+ 条——待办：同设备同类型未恢复报警合并/去重（记入 TECH-DEBT）。

## 四、验证

- **硬件闭环**：温度、湿度双报警分支均实际触发 RELAY_ON 下行，继电器物理跳变（Serial Monitor `[cmd]` + `[relay] ON/OFF` 日志与 EMQX `delivered_msgs` 双向印证）——**GO**；
- **后端**：P2 无生产代码变更，359/359 基线沿用 P1（`d661474`）；`.env` 修正不影响 test profile；
- **数据收尾**：226 条测试报警 `status=2`（resolved_at/resolved_by=1），告警列表恢复干净；
- **Git**：P1 已合并 main（`31e5c61`）并同步远端；P2 产物在 `feat/day99-esp32-p2-closed-loop` 收口。

## 五、遗留与风险

1. 报警合并/去重未实现（见坑位 5，Phase 5 内补）；
2. SSE 真实浏览器端到端验证仍未做（Day 91 P0-4 遗留，Selenium/Playwright）；
3. 节点 B（ESP32 #2 + DHT22 #2 + BH1750 + OLED）接线图册已备，固件未写（`Wire.begin(6,7)` I2C 方案已定，排 Day 100+ 空闲位）；
4. `seed-dev.sh` 预置设备清单与 DB 现状核对（`esp32-dht-001`/`PLC-SIM-001` 已在库，压测设备未核）。

## 六、明日计划（Day 100 — 系统监控）

1. **Spring Boot Actuator + Prometheus**（DAILY_ROADMAP Week 15）：暴露 `/actuator/prometheus`，Micrometer Registry + compose 增加 Prometheus 服务，抓取 JVM/HTTP/MQTT 业务自定义指标；
2. 自定义指标候选：`mqtt_ingest_total`（落库计数）、`mqtt_relay_command_total`（下行计数）、`alarm_triggered_total`（报警计数）——为 Day 102 JMeter 压测建立观测基线；
3. 可选：Grafana 面板（若时间富余，仅装不调优）。

---

> Day 99 结束。Phase 5 里程碑：**MQTT 不再只有模拟器**——真实 ESP32+DHT22+继电器双报警分支闭环实测通过，
> 「传感器 → Broker → 后端 → 报警 → 执行器」完整物理回路打通。下一步 Day 100：Actuator + Prometheus 可观测性。
> 维护者：AI 助手 + hula0710
