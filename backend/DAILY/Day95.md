# Day 95 — Java 模拟 PLC：寄存器区模型 + MQTT 定时发布（Phase 5 Week 15 第 4 天）

> **日期**：2026-09-05
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 4 天
> **分支**：`feat/day95-plc-simulator`
> **配套笔记**：[plc-modbus-learning-notes.md](../../docs/notes/plc-modbus-learning-notes.md) §10
> **验收结果**：✅ **GO**（编译通过 + EMQX 真实收发验证 + telemetry/status/offline retained 均为合法 JSON + 后端 343/343 全绿）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 模拟器 | `backend/learning/java-code/day95/PlcSimulator.java` | 1 台 PLC 模拟器：4 类 Modbus 数据区 + 寄存器原始值/scale/offset + 输入寄存器有界随机游走 + 尖峰/急停/过载/故障联锁 + 1~5s 定时 MQTT 发布 |
| 启动入口 | `backend/learning/java-code/day95/PlcSimulatorMain.java` | 支持 broker / 运行秒数 / 多台 `site/device` 参数，默认 `PLANT_A/PLC-SIM-001` |
| 学习笔记 | `docs/notes/plc-modbus-learning-notes.md` §10 | 模拟器点位表、Topic/Payload 契约、EMQX 验证结果与 Day 96 消费侧约定 |
| 日日志 | `backend/DAILY/Day95.md` | 本文件 |
| 状态同步 | AGENTS §3 / DAILY_ROADMAP Day 95 | 当前基线同步 |

## 二、模拟器设计要点

### 2.1 4 类 Modbus 数据区

模拟器把 Day 92 的“电机控制柜”扩展为可运行点模型，不再把地址写死在业务代码中：

| Modbus 区 | 参考地址 | 点位 | 语义 |
|-----------|---------|------|------|
| Coil 线圈 | 00001 | `motorStart` 启动命令 | 启动命令保持置位 |
| Coil 线圈 | 00002 | `motorRun` 运行状态 | 随急停/过载/运行条件联锁 |
| Discrete Input | 10001 | `estop` 急停按钮 | 正常 1，小概率按下降为 0 |
| Discrete Input | 10002 | `thermalOverload` 热过载触点 | 正常 1，过载事件降为 0 |
| Input Register | 30001 | `current` 电机电流 | `scale=0.1`，量程 8.0~18.0 A |
| Input Register | 30002 | `windingTemp` 绕组温度 | `scale=0.1`，量程 18.0~46.0 °C |
| Input Register | 30003 | `pressure` 管道压力 | `scale=0.1`，量程 90.0~116.0 kPa |
| Input Register | 30004 | `speed` 电机转速 | `scale=1`，量程 0~3400 RPM |
| Holding Register | 40001~40004 | `ratedCurrent` / `tempAlarm` / `pressureHiAlarm` / `speedHiAlarm` | 额定值与报警阈值，本轮只读展示，Downlink 预留 |

工程值换算语义与 Day 92 一致：`physical = raw * scale + offset`。顶层遥测直接使用工程值，
与项目 `device_data.data_type / unit` 对齐；`registerSnapshot` 保留原始寄存器视图供审计。

### 2.2 运行状态机

输入寄存器以正弦趋势 + 随机游走围绕基线变化，每 7 轮小概率触发持续约 3 轮的尖峰并越过
Holding Register 阈值；离散输入小概率出现急停/过载事件并自动回落；线圈 `motorStart` /
`motorRun` 在故障期间联锁清零，故障恢复后重新置位。温度/压力/转速报警阈值与项目
`AlarmRuleConfig` 对齐（40 °C / 110 kPa / 3000 RPM，严格 `>` 比较）。

状态优先级：`estop/thermalOverload` 触发 → `fault`；输入寄存器越阈值 → `alarm`；
否则 `motorRun=1` → `running`，其余为 `standby`。

### 2.3 MQTT Topic 与 QoS

| Topic | QoS | Retained | 用途 |
|-------|:---:|:--------:|------|
| `plc/{siteCode}/{deviceCode}/telemetry` | 1 | 否 | 遥测 + registerSnapshot |
| `plc/{siteCode}/{deviceCode}/status` | 1 | 是 | 在线状态，正常 `online=true`，退出前发布 `online=false,status=offline` |

`status` 是每次发布的 retained 消息，Broker 上始终可读最近状态；`stop()` 发布 offline
retained、断开连接并清理 Paho 持久化临时目录。多个设备可在一个 JVM 中分别启动，每个
设备独立 scheduler 与 Paho client，`runDone` 由 `stop()` 统一 complete。

### 2.4 Payload JSON 契约（Day 96 消费侧草案）

```json
{
  "deviceCode": "PLC-SIM-001",
  "siteCode": "PLANT_A",
  "ts": "2026-09-05T12:00:00+08:00",
  "status": "running",
  "version": "1.0",
  "current": 12.6,
  "windingTemp": 32.5,
  "pressure": 102.5,
  "speed": 1480.0,
  "registerSnapshot": [
    {"area": "inputRegister", "address": 30001, "pointCode": "current",
     "raw": 126, "scale": 0.1, "offset": 0, "unit": "A"}
  ]
}
```

顶层字段只放 device_data 可消费的工程值；`ts` 使用 ISO-8601 带 Asia/Shanghai 时区；
`registerSnapshot` 数组内用 `area/address/pointCode/raw/scale/offset/unit` 表达寄存器原始值。
布尔点（coil/discrete/holding）以 `value: 1|0` 进入同一数组。Day 96 后端 Listener 读取
顶层 `deviceCode/siteCode/dataType` 字段落 `device_data`，不区分 Java 模拟器与真实 ESP32。

## 三、验证

### 3.1 编译

```bash
cd backend
./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/day95-cp.txt
mkdir -p /tmp/day95-out-final
javac -encoding UTF-8 -cp "$(cat /tmp/day95-cp.txt)" \
  -d /tmp/day95-out-final learning/java-code/day95/*.java
```

结果：`COMPILE_OK`。

### 3.2 EMQX 实发验证

```bash
java -cp "/tmp/day95-out-final:$(cat /tmp/day95-cp.txt)" \
  code.day95.PlcSimulatorMain tcp://localhost:1883 14
```

EMQX 5.8.9（`emqx Up 15 hours (healthy)`）实连验证：

- telemetry（QoS 1，非 retained）持续收到完整 JSON，含 4 个输入寄存器工程值与 `registerSnapshot`；
- status（QoS 1，retained）首条为 `online=true`，进程停止前收到 `online=false,status=offline`；
- 独立临时 subscriber 校验 payload 均为非空合法 JSON（不再出现空 `{}`）；
- 模拟器状态机 120 轮观测分布：`running=95 / alarm=23 / fault=2`，尖峰与故障都能自动回落。

### 3.3 后端回归

- `./mvnw test` → **343 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**；
- Day 95 只新增 `backend/learning/java-code/day95/` learning 源码，未改生产 Java 代码，
  不引入新 Maven 依赖，前端基线沿用 Day 91 Exit Audit。

## 四、踩坑与修正

1. **JsonBuilder 缺根对象花括号**：`build()` 原实现只返回拼接内容，导致遥测 payload 曾为 `{}`；
   已改为 `"{" + sb + "}"`，实发验证恢复完整 JSON。
2. **`CompletableFuture` 过早 complete**：首次调度后即 complete 会导致主程序提前退出；
   改为由 `stop()` 完成后 complete。
3. **`spikeUntil` 从未递减**：尖峰被永久保持；改为每轮递减，尖峰持续约 3 轮后回落。
4. **coil/discrete helper 参数错位**：曾把离散点初始值按故障态设置，导致首条 status 为
   `fault`；修正 helper 参数后默认急停/热过载正常为 1，首条 status 为 `running`。

## 五、明日计划（Day 96）

1. 后端生产接入 MQTT Listener：订阅 `plc/+/+/telemetry`，Paho 依赖正式进入 Spring 工程；
2. 按本日志 §2.4 契约解析 JSON → `device_code` 匹配设备 → 写入 `device_data`（先落库、后报警）；
3. MQTT QoS 1 重复投递语义对齐现有 Redis 幂等模式；必要时新增 ADR 0034；
4. 扩展 learning 单测/集成验证，保持后端测试全绿；
5. 创建 Day96.md 日志并同步状态。

---

> 完成时间：2026-09-05（Asia/Shanghai）
> Phase 5 第 4 天状态：Java 模拟 PLC 已能按 4 类 Modbus 数据区持续发布合法 JSON 遥测，
> EMQX 链路验证通过；Day 96 进入真实入站落库。
> 维护者：AI 助手 + hula0710
