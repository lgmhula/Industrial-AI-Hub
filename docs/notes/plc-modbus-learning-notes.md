# Day 92 PLC / Modbus 学习笔记：从线圈寄存器到工业数据接入

> 日期：2026-09-04 | 覆盖：Phase 5 Day 92（PLC 基础概念：Modbus / 寄存器 / 线圈）
> 配套文档：[Day92.md](../../backend/DAILY/Day92.md) / [DAILY_ROADMAP](../../backend/DAILY_ROADMAP.md) / [Application-Architecture.md](../Architecture/Application-Architecture.md)
> 阶段定位：Phase 5 概念日，不写代码；Day 93 起开始落地 MQTT 与 Java 客户端。

---

## 1. Day 92 在 Phase 5 中的位置

Phase 5 的目标是「PLC 模拟设备接入 + MQTT 协议 + 完整系统上线」。最终的数据链路不是让
Industrial AI Hub 后端直接去轮询 PLC，而是通过工业网关/边缘程序做协议转换：

```text
PLC 扫描现场信号
  │
  │  Modbus RTU/TCP（寄存器 / 线圈 / 离散输入）
  ▼
IoT 网关或边缘程序（Modbus client/master）
  │
  │  MQTT publish（Day 93-95）
  ▼
MQTT Broker（EMQX / Mosquitto）
  │
  │  MQTT subscribe（Day 96）
  ▼
Industrial AI Hub 后端
  │
  ├─ device_data 落库 → 规则引擎/Alarm
  ├─ AI 分析（巡检 / 诊断 / 摘要）
  └─ SSE / 前端实时展示
```

所以 Day 92 先回答三件事：

1. PLC 是怎么「跑」的（扫描周期、I/O 映射）；
2. Modbus 是什么协议，为什么工业现场到处都是它；
3. 线圈、离散输入、输入寄存器、保持寄存器到底有什么差别，未来如何映射到 `device_data`。

---

## 2. PLC 基础：扫描周期与 I/O 映射

### 2.1 PLC 的循环执行模型

PLC（Programmable Logic Controller，可编程逻辑控制器）不是事件驱动的，而是循环扫描执行用户程序：

```text
┌─────────────────────────────────────────────────────────┐
│                  PLC 扫描周期（一个 cycle）                  │
├─────────────────────────────────────────────────────────┤
│ 1. 输入刷新：把物理输入点（按钮/传感器）批量读入「输入映像区」       │
│ 2. 程序执行：从第一条指令到最后一条，逐条执行用户程序（梯形图/ST）    │
│ 3. 输出刷新：把「输出映像区」批量写到物理输出点（继电器/接触器）      │
│ 4. 系统处理：通信、诊断、自检、定时器等由系统占用                │
└─────────────────────────────────────────────────────────┘
```

关键理解：

- 输入刷新和输出刷新是**整批**进行的，程序执行期间 I/O 状态不会中途抖动；
- 用户程序只操作映像区（input image / output image），而不是直接读写物理点；
- 扫描周期 = 输入刷新 + 程序执行 + 通信/系统处理 + 输出刷新。周期越短，实时性越好，
  但循环次数、通信数据量和复杂指令都会拉长周期；
- 梯形图按「从上到下、从左到右」扫描每个 rung（梯级），这也是“PLC 程序是顺序循环”的来源。

### 2.2 梯形图概念

梯形图是 PLC 最常见的编程语言，视觉上像继电器控制电路：

```text
  ┌─[启动]──[停止常闭]─────────────(电机线圈)─┐
  │                                         │
  └─[电机自保持]──────────────────────────────┘
```

- `[ ]`：常开触点，条件为真时闭合；
- `[/]`：常闭触点，条件为假时闭合；
- `( )`：线圈，把结果写进输出映像区；
- 上例是经典“启保停”回路：按下启动后电机运行，释放启动按钮后靠自保持维持。

对我们的项目来说，不需要从零写梯形图，但要理解“PLC 输出点最终来自某个程序运算结果”，
否则容易把 Modbus 数据当成“传感器原样读数”，忽略 PLC 程序可能已经做过联锁、滤波、量程换算。

### 2.3 I/O 点与 Modbus 数据不是一回事

物理 I/O 点（`I0.0`、`Q0.0` 这类）属于 PLC 厂家命名；Modbus 可访问的是 PLC 程序里映射出的
**数据区**。同一台设备读到的地址由厂商手册或组态工具决定，不能只看 Modbus 功能码就断定物理含义。
这也是 Phase 5 模拟 PLC 时要先固定「地址表 / 点位表」的原因。

---

## 3. Modbus 协议模型

### 3.1 是什么

Modbus 是 1979 年由 Modicon 提出的工业串行通信协议，后来由 Modbus Organization 维护。
它简单、开放、帧结构清晰，是 PLC、仪表、变频器、能源采集器最通用的数据接口之一。

协议本身只关心「主设备问、从设备答」的数据交换，不负责设备发现、安全认证和传输可靠性，
可靠性主要靠：

- RS-485/以太网物理链路；
- 请求-响应模型；
- RTU 帧 CRC 校验；
- 应用层功能码与异常码。

### 3.2 主从 / 客户端-服务器术语

| 旧术语 | 新术语（Modbus 规范） | 行为 |
|--------|----------------------|------|
| Master（主站） | Client（客户端） | 主动发起请求：读数据、写数据 |
| Slave（从站） | Server（服务器） | 被动等待请求，返回数据或异常 |

串行 RS-485 上一个网络中通常只有 1 个 Client，最多 247 个 Server（地址 1-247，0 为广播）；
Modbus TCP 上 Client/Server 数量更灵活，但本质上仍是「一问一答」。

### 3.3 常见传输形态

| 形态 | 物理层 | 特点 | 本项目定位 |
|------|--------|------|-----------|
| Modbus RTU | RS-232 / RS-485，二进制 | 最常用，帧短，CRC 校验 | 模拟 PLC 时首选学习对象 |
| Modbus ASCII | RS-232 / RS-485，ASCII | 可读但帧长，LRC 校验 | 了解即可 |
| Modbus TCP | 以太网，默认端口 502 | 无从站地址网络约束，MBAP 头 | 模拟 PLC/网关最接近真实 |

---

## 4. 四类数据区：线圈 / 离散输入 / 输入寄存器 / 保持寄存器

这是 Day 92 最重要的一张表：

| 数据区 | 数据宽度 | 访问方向 | 功能码 | 典型含义 |
|--------|---------|---------|--------|---------|
| **Coil（线圈）** | 1 bit | 读 / 写 | 01 读线圈；05 写单线圈；15 写多线圈 | 启停、继电器、阀门开关、报警复位 |
| **Discrete Input（离散输入）** | 1 bit | 只读 | 02 读离散输入 | 限位开关、按钮状态、光电开关、故障干接点 |
| **Input Register（输入寄存器）** | 16 bit | 只读 | 04 读输入寄存器 | 温度、压力、电流等模拟量采集值 |
| **Holding Register（保持寄存器）** | 16 bit | 读 / 写 | 03 读保持寄存器；06 写单寄存器；16 写多寄存器 | 设定值、速度、PID 参数、可读写变量 |

助记：

- **Coil** 是“我能改变它”的开关量；
- **Discrete Input** 是“只能看”的开关量；
- **Input Register** 是“设备测量的、只读的数值”；
- **Holding Register** 是“既保存又允许上位机修改的数值”。

### 4.1 地址引用与协议偏移

Modbus 传统用 5 位十进制“参考地址”帮助人记忆：

| 参考地址段 | 数据区 | 例子 |
|-----------|--------|------|
| `0xxxx` | Coil | `00001` = 第 1 个线圈 |
| `1xxxx` | Discrete Input | `10001` = 第 1 个离散输入 |
| `3xxxx` | Input Register | `30001` = 第 1 个输入寄存器 |
| `4xxxx` | Holding Register | `40001` = 第 1 个保持寄存器 |

需要注意：参考地址通常从 1 开始，而 Modbus **PDU 内地址偏移从 0 开始**。
例如“读 40001”在报文中写的是 offset `0x0000`；“读 40003”写 `0x0002`。
多数开发库（如 jlibmodbus / jamod）会封装这个差别，但抓包/看手册时必须能对回来。

### 4.2 寄存器不是“一个变量占一个寄存器”这么简单

- 单个寄存器是 16 位无符号整数，范围 0-65535；
- 32 位浮点/整数通常占 2 个寄存器，字节序有 `ABCD / BADC / CDAB / DCBA` 四种组合；
- 负数、浮点、带符号整数必须按设备手册的寄存器组解释；
- 量程换算常见为 `物理值 = 原始值 × scale + offset`，例如 `23.5 °C = 235 × 0.1`。

---

## 5. 常用功能码与报文结构

### 5.1 常用功能码速查

| 功能码 | 名称 | 用途 |
|--------|------|------|
| `01 (0x01)` | Read Coils | 读多个线圈状态 |
| `02 (0x02)` | Read Discrete Inputs | 读多个离散输入 |
| `03 (0x03)` | Read Holding Registers | 读多个保持寄存器 |
| `04 (0x04)` | Read Input Registers | 读多个输入寄存器 |
| `05 (0x05)` | Write Single Coil | 写一个线圈（`FF00`=ON，`0000`=OFF） |
| `06 (0x06)` | Write Single Register | 写一个保持寄存器 |
| `15 (0x0F)` | Write Multiple Coils | 连续写多个线圈 |
| `16 (0x10)` | Write Multiple Registers | 连续写多个保持寄存器 |

其他还有诊断（08）、读异常状态（07）、Mask/Read-Write（22/23）等，先不作为学习重点。

### 5.2 PDU 与 ADU

Modbus 把“和具体传输无关的功能请求”称为 **PDU（Protocol Data Unit）**：

```text
PDU = Function Code + Data
```

加上传输层头部/尾部后称为 **ADU（Application Data Unit）**：

- RTU：`从站地址 + PDU + CRC16`
- TCP：`MBAP 头 + PDU`，MBAP = Transaction ID + Protocol ID + Length + Unit ID

### 5.3 读保持寄存器示例（RTU 与 TCP 对照）

请求：读 Server address `1` 的 Holding Register `40001`（PDU offset `0000`），数量 1 个。

RTU 报文（CRC 仅示意，实际需计算）：

```text
01 03 00 00 00 01 CRC_LOW CRC_HIGH
│  │  └─地址─┘ └─数量─┘
│  └ 03 = Read Holding Registers
└ 01 = Server Address
```

正常响应：

```text
01 03 02 00 0B CRC_LOW CRC_HIGH
      │  └─数据：寄存器值 = 0x000B = 11
      └ 02 = 后续字节数
```

TCP 报文多了 7 字节 MBAP 头：

```text
Transaction_ID(2) Protocol_ID=0x0000(2) Length(2) Unit_ID(1) + 上述 PDU
```

### 5.4 RTU 帧细节

RTU 每帧之间需要至少 **3.5 个字符时间的静默间隔**，帧内字节间隔不能超过 1.5 个字符时间，
否则接收方可能判为帧错误。CRC16 采用多项式 `0xA001`（Modbus 变体），校验从站地址开始的所有内容。

---

## 6. 异常响应

Server 收到无法处理的请求时返回：

```text
Server Address + Function Code | 0x80 + Exception Code + CRC
```

常见异常码：

| 异常码 | 含义 |
|--------|------|
| `01` | 非法功能码 |
| `02` | 非法数据地址 |
| `03` | 非法数据值 |
| `04` | Server 设备故障 |
| `06` | Server 设备忙 |

Phase 5 模拟器应把异常码当作正常业务路径实现，而不是直接抛异常导致连接断掉。

---

## 7. 一个综合例子：电机控制柜

假设一台电机控制柜由 PLC 管理，点位表如下：

| 地址 | 数据区 | 含义 | 读写 |
|------|--------|------|------|
| `00001` | Coil | 电机启动命令 | 写 |
| `00002` | Coil | 电机运行状态 | 读 |
| `10001` | Discrete Input | 急停按钮状态 | 只读 |
| `30001` | Input Register | 电机电流（0.1 A） | 只读 |
| `30002` | Input Register | 绕组温度（0.1 °C） | 只读 |
| `40001` | Holding Register | 额定电流设定（0.1 A） | 读写 |

上位机流程：

```text
写 00001 = ON       → 启动命令
读 00002 = 1        → 运行反馈
读 10001 = 0        → 急停未按下
读 30002 原始值 235  → 23.5 °C
写 40001 = 500      → 额定电流设为 50.0 A
```

这些点经过网关转为 MQTT 后，就可以进入项目 `device_data` / `alarm`：

| Modbus 点位 | 项目落点 | 说明 |
|------------|---------|------|
| 电流/温度输入寄存器 | `device_data.data_type` + `data_value` | 数值型遥测 |
| 运行状态线圈 | 可扩展为布尔型数据点 | 状态型遥测 |
| 急停离散输入 | `alarm` 触发条件 | 边界事件 |
| 额定电流保持寄存器 | 设备配置/参数下发 | 只读演示阶段可不写 |

---

## 8. 对 Industrial AI Hub 的工程启示

1. **分地址段建模**：模拟器要先定义寄存器区/线圈区地址表，再谈采集，避免把地址写死在业务代码；
2. **数值语义在点位表**：温度是 0.1 精度还是 0.01 精度、有没有偏移，应由点位配置决定，入库前完成换算；
3. **状态与事件分离**：线圈状态适合存最近值/状态快照，离散输入的跳变才适合生成报警事件；
4. **多副本与实时性**：Day 85 推送链路和 Redis 幂等模式可以在 MQTT 入站后复用，但 MQTT QoS 1/2 的重复投递语义要在 Day 93 对齐；
5. **当前 `device_data.data_type` 约束只有温度/压力/速度/湿度/电流**，Phase 5 接 PLC 点位时需要评估扩展 `CHECK` 或改由主题表驱动，不是 Day 92 编码日要做的决定。

---

## 9. 学习检查表

- [x] 能画出 PLC 扫描周期（输入刷新 → 程序执行 → 输出刷新）;
- [x] 能说明梯形图「常开/常闭触点 + 线圈」的基本执行模型;
- [x] 能说出 Modbus 旧术语 Master/Slave 与新术语 Client/Server;
- [x] 能背出四类数据区的读写方向与 01/02/03/04/05/06/15/16 功能码;
- [x] 能区分参考地址（1 起）与 PDU offset（0 起）;
- [x] 能读简单 RTU 十六进制帧并知道 CRC、静默间隔的意义;
- [x] 能指出 32 位值跨 2 个寄存器和字节序是点位表问题，不是代码随便猜的;
- [x] 能画出 PLC → 网关 → MQTT → 本项目 → AI/告警/SSE 的接入链路。

---

> Day 92 概念日结束。明天 Day 93 进入 MQTT 协议基础 + EMQX/Mosquitto 安装，
> 然后在 Java 端把“模拟 PLC/网关”真正发布出来。

---

## 10. Day 95：Java 模拟 PLC 寄存器区模型与 MQTT 发布（2026-09-05）

### 10.1 代码与职责边界

Day 94 打通 Paho publish/subscribe 后，Day 95 把它升级为“一台 PLC 定时上报”的模拟器，
代码只放在 learning 目录，不进 Spring 生产工程：

| 文件 | 职责 |
|------|------|
| `backend/learning/java-code/day95/PlcSimulator.java` | 单台设备模型 + 状态机 + 定时发布 |
| `backend/learning/java-code/day95/PlcSimulatorMain.java` | broker/秒数/多设备 CLI 启动入口 |

Phase 5 的接入边界是“模拟 PLC/网关先证明 Topic 契约，后端 Day 96 再订阅落库”，
因此模拟器不依赖 Spring、不依赖 `device_data` 表结构，只保证工程值与 Topic 语义可被消费。

### 10.2 电机控制柜点位表（可运行版本）

| 参考地址 | 区 | pointCode | 工程量程 / 单位 | scale | 说明 |
|---------|-----|-----------|----------------|:-----:|------|
| `00001` | Coil | `motorStart` | 0/1 | - | 启动命令，故障联锁清 0 |
| `00002` | Coil | `motorRun` | 0/1 | - | 运行反馈 |
| `10001` | Discrete Input | `estop` | 0/1 | - | 急停，正常 1、按下 0 |
| `10002` | Discrete Input | `thermalOverload` | 0/1 | - | 热过载触点 |
| `30001` | Input Register | `current` | 8.0~18.0 A | 0.1 | 电机电流 |
| `30002` | Input Register | `windingTemp` | 18.0~46.0 °C | 0.1 | 绕组温度 |
| `30003` | Input Register | `pressure` | 90.0~116.0 kPa | 0.1 | 管道压力 |
| `30004` | Input Register | `speed` | 0~3400 RPM | 1 | 电机转速 |
| `40001` | Holding Register | `ratedCurrent` | 15.0 A | 0.1 | 额定电流设定 |
| `40002` | Holding Register | `tempAlarm` | 40.0 °C | 0.1 | 温度报警阈值 |
| `40003` | Holding Register | `pressureHiAlarm` | 110.0 kPa | 0.1 | 压力上限 |
| `40004` | Holding Register | `speedHiAlarm` | 3000.0 RPM | 1 | 转速上限 |

输入寄存器保存 `value` 工程值，同时维护 `scale/offset`，任何时刻可由
`raw = round(value / scale)` 还原寄存器原始整型。Holding Register 在 Day 95 只做
“参数区读取与阈值联动”，写寄存器（05/06/16 功能码）留给真实 PLC/网关演示。

### 10.3 MQTT Topic 命名空间

```text
plc/{siteCode}/{deviceCode}/telemetry   QoS 1, retained=false   每 1~5 秒 1 条遥测
plc/{siteCode}/{deviceCode}/status      QoS 1, retained=true    在线状态 / 退出前 offline
```

默认设备 `PLANT_A/PLC-SIM-001`。ESP32 #1 + DHT22 烧录后按同一命名空间
`plc/PLANT_A/esp32-dht-001/telemetry` 上报即可与模拟器共存；后端 Day 96 的 Listener
不需要区分数据源，只依赖 payload 中的 `siteCode/deviceCode`。

### 10.4 Payload 契约要点

顶层字段是给业务层消费的：

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

`status` 主题的 payload 更短：`deviceCode/siteCode/ts/online/status`。对 Day 96 的契约建议：

1. 后端 Listener 订阅 `plc/+/+/telemetry`，**不做 retained 依赖**，以业务表为准；
2. QoS 1 可能重复投递，入库前复用项目 Redis 幂等键思路（`mqtt:{deviceCode}:{ts}`）；
3. `ts` 用 ISO-8601 带时区，与 `device_data` 时间戳语义对齐；
4. 设备编码需要与 `device.device_code` 匹配；seed 预置的是 `PLC-M-001` / `PLC-A-*`，
   `PLC-SIM-001` 不会自动存在——联调时先用 `PLC-M-001`，或先注册模拟器设备再启动 Listener；
5. 布尔遥测与报警事件分开：线圈/离散状态适合“状态快照”，跳变才落 `alarm`（沿用 Day 92 §8 结论）。

### 10.5 EMQX 验证结论

EMQX 5.8.9 容器健康，`PlcSimulatorMain tcp://localhost:1883 14` 实跑时：

- 独立 subscriber 能收到完整 telemetry，QoS 1、非 retained；
- status 首条 retained `online=true`，进程停止前收到 retained `online=false, status=offline`；
- payload 均为非空合法 JSON（修复 JsonBuilder 缺花括号后）；
- 状态机 120 轮观测 `running=95 / alarm=23 / fault=2`，说明尖峰越阈值和故障恢复语义可验证。

### 10.6 Day 95 结论

Java 端已经从“协议冒烟”进入“设备仿真”：模拟器拥有四类寄存器区的统一点模型、
scale/offset 工程换算、可触发的阈值尖峰与急停/过载事件、QoS 1 + retained 状态发布，
足以支撑 Day 96 生产 Listener 的联调输入；真实 ESP32 接入不会改变 Topic 与 JSON 契约。

> Day 95 模拟设备日结束。明天 Day 96 开始 MQTT → Spring Boot：订阅 `plc/+/+/telemetry`，
> 把 Java 模拟 PLC 的报文变成 `device_data` 真实记录。
