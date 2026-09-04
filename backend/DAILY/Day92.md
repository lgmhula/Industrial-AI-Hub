# Day 92 — PLC 基础概念：Modbus、寄存器、线圈（Phase 5 启动）

> **日期**：2026-09-04
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 1 天
> **分支**：`feat/day92-plc-basics`
> **配套笔记**：[plc-modbus-learning-notes.md](../../docs/notes/plc-modbus-learning-notes.md)
> **验收结果**：✅ **GO**（概念笔记成稿；无代码变更，无回归风险）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 学习笔记 | `docs/notes/plc-modbus-learning-notes.md` | PLC 扫描周期、梯形图概念、Modbus RTU/TCP、四类数据区、功能码/报文、点位表建模 |
| 日日志 | `backend/DAILY/Day92.md` | 本文件 |
| 状态同步 | AGENTS §3 / DAILY_ROADMAP Day 92 标记完成 | Phase 5 启动，下一步 Day 93 MQTT |

## 二、学习结论

### 2.1 PLC 执行模型

- PLC 是**循环扫描**执行：输入刷新 → 程序执行 → 输出刷新 → 系统处理；
- 用户程序操作输入/输出映像区，批量刷新物理 I/O；
- 梯形图由常开触点、常闭触点和线圈组成，按梯级顺序扫描；
- 物理 I/O 点与 Modbus 数据区不是一回事，点位表决定语义。

### 2.2 四类 Modbus 数据

| 数据区 | 宽度 | 读写 | 典型含义 |
|--------|------|------|---------|
| Coil | 1 bit | 读/写 | 启停、阀门、报警复位 |
| Discrete Input | 1 bit | 只读 | 限位、急停、干接点 |
| Input Register | 16 bit | 只读 | 模拟量采集 |
| Holding Register | 16 bit | 读/写 | 设定值、可读写参数 |

### 2.3 关键工程注意点

1. Modbus 参考地址从 1 开始，PDU offset 从 0 开始；
2. RTU 依赖 CRC16 + 帧间静默；TCP 多 7 字节 MBAP 头；
3. 32 位值跨 2 个寄存器，字节序必须来自设备点位表；
4. 模拟 PLC 时应先建模「寄存器区/线圈区 + 量程/单位/偏移」，再写采集代码。

## 三、与 Phase 5 后续任务的关系

```text
PLC 点位表（线圈/离散输入/输入寄存器/保持寄存器）
  → Modbus RTU/TCP 报文（Day 92 概念）
  → MQTT 协议（Day 93）
  → Java MQTT 客户端（Day 94）
  → Java 模拟 PLC 定时发布（Day 95）
  → MQTT → device_data 落库（Day 96）
  → 多设备并发 + 压测（Day 97）
```

## 四、验证

- 纯文档日，无 Java/Vue 代码改动；
- 后端 343 tests + 前端 build 基线沿用 Day 89/91；
- Git 分支收口：本日从合并后的 `main`/`pc_hula` 同点 `085092f` 拉出
  `feat/day92-plc-basics`，完成后并入两条分支并删除，保留 `main` + `pc_hula`。

## 五、明日计划（Day 93）

1. MQTT 协议基础：Broker/Topic/Publish/Subscribe/QoS 0/1/2/保留消息；
2. compose.yml 增加 EMQX 或 Mosquitto（优先按 DAILY_ROADMAP 方案选型）；
3. `docs/notes/` 追加 MQTT 学习笔记；
4. 后端测试与前端 build 保持全绿。

---

> 完成时间：2026-09-04（Asia/Shanghai）
> Phase 5 启动声明：Day 92 完成 PLC/Modbus 概念闭环，Day 93 进入 MQTT 协议落地。
> 维护者：AI 助手 + hula0710
