# Industrial AI Hub 简历材料

> 适用岗位：Java 后端开发
> 事实基线：Day 111 / 2026-10-18
> 使用原则：项目数字必须可追溯；未完成能力必须保留边界；不得虚构学历、公司、任职时间、职责或线上规模。

---

## 一、简历定位

Industrial AI Hub 应当作为“独立完成的复杂后端项目”呈现，而不是公司生产系统。推荐定位：

> 独立设计并实现工业设备管理与 AI 分析平台，重点展示 Java 后端、物联网接入、消息可靠性、AI 工程化和可观测性能力。

可搭配的真实工作经历应单独填写。本文件只提供项目条目，不代替个人经历，也不把个人项目包装成公司项目。

### 填写占位符

- `[姓名]`
- `[手机号]`
- `[邮箱]`
- `[GitHub 仓库地址]`
- `[求职城市]`
- `[可到岗时间]`
- `[真实工作经历 1]`
- `[真实工作经历 2]`

---

## 二、三个版本的项目条目

### 1. 一句话版

> 独立开发 Industrial AI Hub 工业设备管理与 AI 分析平台，打通 ESP32 温湿度采集、MQTT 幂等落库、规则报警、继电器下行、AI 巡检和 SSE 推送链路，后端 375 个测试全绿。

### 2. 紧凑版

**Industrial AI Hub | 个人项目 | 独立开发 | [GitHub 仓库地址]**

- 使用 Java 25、Spring Boot 3.5、MyBatis、MySQL、Redis、RabbitMQ、EMQX 和 Vue 3 构建工业设备管理与 AI 分析平台。
- 实现 JWT/RBAC、站点数据隔离、设备与遥测管理、8 条报警规则、操作审计、缓存、异步消息和统一异常处理。
- 打通真实 ESP32-S3 + DHT22 到后端的 MQTT QoS1 链路，以 Redis 字段级 SETNX 实现 24 小时幂等，并完成温度/湿度报警到继电器下行的物理闭环。
- 接入告警摘要、设备诊断、RAG 问答、Agent 巡检日报和 AI 自动报警；使用 7 个只读 MCP 工具并限制 Agent 循环轮数。
- 建立 375 个后端测试、37 份 ADR、Prometheus 指标、traceId 日志和 JMeter 压测证据，Compose 全量 15 服务预演通过。

### 3. 扩展版

**Industrial AI Hub | Java 后端 / 独立开发 | [起止时间] | [GitHub 仓库地址]**

**项目简介**

面向工业设备管理场景的全栈项目。后端采用 Java 25、Spring Boot 3.5 和 MyBatis，前端采用 Vue 3，基础设施使用 Docker Compose；覆盖设备接入、遥测存储、报警处理、AI 分析、异步推送和运维观测。

**主要工作**

- **IoT 双向链路**：通过 Eclipse Paho 接入 EMQX，消费 `plc/{siteCode}/{deviceCode}/telemetry` QoS1 消息；按设备、时间戳和字段类型构造 Redis SETNX 幂等键，TTL 24 小时；落库后触发报警，并向 command Topic 下发继电器命令，使用 Redis 5 秒节流和固件 3 秒自动复位形成双重保护。
- **认证与数据权限**：实现 JWT 登录、BCrypt 密码校验、ADMIN/OPERATOR/VIEWER 三级角色和 `user_site` 站点资源作用域；非管理员只能访问被授权站点，关键写操作通过 AOP 写入审计日志。
- **消息可靠性**：设计 RabbitMQ 工作队列、Fanout、DLQ 和延迟检查链路；巡检日报消费端使用手动 ACK、Redis SETNX 幂等和失败不重回队列策略，避免重复消费与毒消息阻塞。
- **AI 工程化**：基于 Spring AI 接入 DeepSeek，实现告警摘要、设备诊断、RAG 知识库问答、Agent 巡检日报和 AI 异常自动转报警；关闭框架自动工具执行，对普通 Agent 设置 3 轮、巡检 Agent 设置最多 6 轮的硬限制，MCP 仅暴露 7 个只读工具。
- **可观测性与性能**：通过 Actuator、Micrometer 和 Prometheus 暴露 JVM、HTTP 与三条 MQTT 业务指标；实现 `X-Trace-Id` 全链路透传和滚动日志；使用 JMeter 验证限速内读写性能、突发限流和 traceId 回归。
- **测试与发布**：维护 375 个后端测试，覆盖 MQTT 幂等矩阵、SSE/RabbitMQ 真实中间件集成测试、限流并发和全局异常处理；使用 Flyway 管理 V1 + V3-V15 共 14 个活动迁移；完成宿主机与容器双路径发布预演。

**项目结果**

- 真实 ESP32 温湿度数据完成“传感器 → EMQX → 后端 → Redis 幂等 → MySQL → 规则报警 → 继电器”的物理闭环。
- JMeter 实测读接口 40.3 req/s、平均 8.5 ms、p99 18 ms，2415 个样本全部返回 200；写接口 20.1 req/s，1208 条样本全部成功并落库。
- 突发施压约 4639 req/s 时，系统按设计放行约 52.5 req/s，拒绝率 98.87%；142,771 个样本的 traceId 回传断言零失败。
- 限流拒绝日志从 137,568 行降至 6 行，日志体积下降 92.8%，放行数量与慢样本绝对数保持不变。
- 默认 Compose 6 服务可直接启动，`--profile full` 的 15 服务完成 15/15 healthy 预演。

---

## 三、技术栈写法

### 推荐版本

`Java 25`、`Spring Boot 3.5`、`MyBatis`、`MySQL 8.4`、`Flyway`、`Redis Stack`、`RabbitMQ`、`Eclipse Paho / MQTT`、`EMQX`、`Spring AI`、`DeepSeek API`、`MCP`、`Spring Boot Actuator`、`Micrometer`、`Prometheus`、`JWT`、`Docker Compose`、`Vue 3`、`Vite`、`Element Plus`

### 按能力分组

| 能力 | 技术 |
|------|------|
| 后端基础 | Java 25、Spring Boot 3.5、MyBatis、Spring MVC、Bean Validation |
| 数据与迁移 | MySQL 8.4、Flyway、PageHelper、Redis Stack |
| 消息与 IoT | RabbitMQ、EMQX、MQTT QoS1、Eclipse Paho |
| AI | Spring AI、DeepSeek、RAG、Agent、Function Calling、MCP、PDFBox |
| 安全 | JWT、BCrypt、RBAC、站点作用域、Guava RateLimiter |
| 观测与测试 | Actuator、Micrometer、Prometheus、JUnit 5、Mockito、MockMvc、JMeter |
| 交付 | Docker Compose、Nginx、Vue 3、Vite |

不要为了匹配 JDK 关键词把实际项目写成 JDK 8 或 JDK 17。仓库与容器基线均为 JDK 25。

---

## 四、量化证据

| 指标 | 统一口径 | 证据 |
|------|----------|------|
| 后端测试 | 375/375，0 failures / 0 errors / 0 skipped | Day 111 全量回归 |
| 架构决策 | 37 份 ADR，编号 0001-0037 | `docs/decision-log/` |
| 数据库 | Schema V15，14 个活动迁移，10 张表 | Flyway / ADR 0012 |
| 编排 | 默认 6 服务；full profile 15 服务全部 healthy | Day 104、Day 111 预演 |
| MQTT 并发 | 20 台设备 × 10 msg/s，201.3 msg/s，3020 条 100% ACK | Day 97 |
| HTTP 读压测 | 40.3 req/s，平均 8.5 ms，p99 18 ms，100% 200 | Day 102 |
| HTTP 写压测 | 20.1 req/s，平均 22 ms，1208 条全部成功并落库 | Day 102 |
| 突发限流 | 约 4639 req/s 输入，放行约 52.5 req/s，拒绝率 98.87% | Day 102 |
| traceId 回归 | 142,771 样本，0 失败 | Day 102 |
| 日志治理 | 拒绝日志行数下降 99.996%，体积下降 92.8% | Day 103 |
| MCP 工具 | 7 个只读工具 | ADR 0027-0030 |

数字使用规则：

1. `375` 是当前测试用例总数，不等同于生产质量结论。
2. `4639 req/s` 是压测输入压力，不是系统吞吐能力。
3. `52.5 req/s` 是当时路径限流后的放行速率，不是容量承诺。
4. `15 服务` 是 Compose 服务数量，不代表系统已经拆成微服务。

---

## 五、STAR 故事

### 故事一：真实硬件双向闭环

**Situation**：项目已有模拟 PLC 和 MQTT 链路，但仅验证消息可发送，不能证明传感器数据能驱动真实执行器。

**Task**：打通 ESP32-S3、DHT22、EMQX、Java 后端、报警规则和继电器的完整物理链路，同时处理 QoS1 重复消息与重复执行风险。

**Action**：定义 telemetry/command Topic 和 payload 契约；后端使用 Redis `mqtt:{deviceId}:{ts}:{dataType}` SETNX 做字段级幂等；报警命中后通过 command Topic 下发继电器命令，Redis 做 5 秒节流；固件收到命令后吸合 3 秒自动复位。

**Result**：静置基线 29°C/52%RH；哈气到 100%RH 触发 `OVER_HUMIDITY`；热风到 68°C 触发 `OVER_TEMP`；两条报警分支均驱动继电器，停止刺激后恢复到约 30°C/49.7%RH。

**可追问证据**：ADR 0034/0035、Day 99 每日日志、硬件接线图册。

### 故事二：用同一份压测数据撤回错误结论

**Situation**：首次 JMeter 突发压测出现约 500 ms 的 p99，最初怀疑限流器 `tryAcquire(500ms)` 阻塞线程。

**Task**：判断延迟究竟来自 429 拒绝路径，还是来自被放行请求进入有界背压队列后的排队。

**Action**：不直接改 SQL、缓存或线程池，而是对同一份 JTL 按 `responseCode` 拆分；分别统计 200 和 429 的平均值、分位数和慢样本绝对数量。

**Result**：429 平均 0.9 ms、p99 3 ms，拒绝路径并不阻塞；约 500 ms 主要出现在被放行的 200 样本。后续只修复有证据的日志放大器，拒绝日志从 137,568 行降到 6 行，体积下降 92.8%，业务放行行为不变。

**可追问证据**：Day 102/103 报告、ADR 0037。

### 故事三：发布预演暴露容器目录缺失

**Situation**：宿主机测试和普通启动均通过，但 full-profile 容器预演时，向量库持久化目录 `/app/data` 不存在，应用回退到 `./data` 并产生 WARN。

**Task**：让容器运行用户拥有可写数据目录，并确认修复不是只在外部 `docker exec` 中临时生效。

**Action**：在运行镜像创建 `/app/data` 和 `/app/logs` 并统一授权给 `app` 用户；重建后端镜像；检查目录所有者、写入探针、健康端点和启动日志。

**Result**：容器以非 root 用户运行时可写入 `/app/data`，启动日志不再出现目录回退 WARN，Actuator health 保持 UP。

**可追问证据**：Day 111 每日日志、`backend/Dockerfile`。

---

## 六、可以写与不能写

| 主题 | 可以写 | 不能写 |
|------|--------|--------|
| 项目归属 | 独立设计、独立开发、个人项目 | 写成公司生产系统或团队项目 |
| 硬件 | 1 台 ESP32-S3 完成真实双向闭环 | 已批量接入设备、已支持上千节点 |
| MQTT 幂等 | Redis SETNX 字段级幂等，Redis 故障时降级继续落库 | exactly-once、任何故障下绝不重复 |
| RabbitMQ | 手动 ACK、DLQ、Redis 幂等、失败不重回队列 | 已实现完整分布式事务 |
| SSE | 链路有 RabbitMQ/Redis 集成测试，浏览器级 E2E 未补 | 浏览器端到端测试已全覆盖 |
| RAG | `SimpleVectorStore + LocalHashEmbeddingModel` 已打通 | 已接入 Qdrant 或生产级向量数据库 |
| AI | DeepSeek 是 opt-in，未启用时返回 503 | AI 默认在线、核心业务依赖大模型 |
| 多副本 | Redis 幂等可跨实例 | SSE、Guava 限流、日志节流已分布式化 |
| Compose | full profile 15/15 healthy 已验证 | 已拆分为 15 个业务微服务 |
| 性能 | 给出压测条件和实测数字 | 把施压值当容量，或承诺生产 SLO |
| 前端 | Vue 3 页面、SSE、AI 交互已落地 | 主导大型前端团队或设计系统平台 |
| 工作经历 | 只写真实公司、时间和职责 | 把路线图日期、学习项目或他人成果写成任职经历 |

### 高频夸大风险

- 不写“高并发生产系统”：当前是本地实测与单机编排，没有线上流量。
- 不写“分布式限流”：Guava 桶是 per-JVM。
- 不写“百万设备接入”：只验证了 20 台模拟器和 1 台真实设备。
- 不写“Qdrant 已落地”：Qdrant 仍是演进目标。
- 不写“全链路可观测平台”：已有 Prometheus、traceId 和滚动日志，但没有 Grafana、链路追踪后端或集中日志平台。
- 不写“生产级高可用”：尚未完成多副本、无状态化和故障切换。

---

## 七、投递前检查

- [ ] 姓名、电话、邮箱、城市、到岗时间已替换为真实信息。
- [ ] 公司、职位、起止时间和职责均来自真实经历。
- [ ] 项目开始与结束时间按实际开发周期填写，不把 112 天路线写成任职时间。
- [ ] GitHub 仓库可访问，README 能复现启动方式。
- [ ] 简历中的每个数字都能在报告中找到来源。
- [ ] 面试时能解释 MQTT QoS1 与 Redis 幂等、RabbitMQ ACK/DLQ、SSE 生命周期和限流桶边界。
- [ ] 对未完成项使用“当前边界 + 下一步”，不回避追问。
- [ ] 针对不同 JD 调整技术栈排序，但不修改事实。

---

> 这份材料的目标不是把项目写得更大，而是让每个结论都能被代码、测试、报告或实测记录支撑。
