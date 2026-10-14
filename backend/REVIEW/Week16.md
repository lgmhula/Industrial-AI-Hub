# Week 16 复盘 — 完整系统上线周：物理闭环 → 观测 → 压测 → 优化 → 编排（Day 99-105）

> 日期：2026-10-14 | 覆盖：Day 99 ~ Day 104（Week 16 第 7 天 = Day 105 复盘日，本文件）
> DAILY_ROADMAP 对应段：「第 15 周：系统整合 + 运维」（Day 99-105）
> 基线演进：355 → **375 tests**（+20：Day 99 P1 +4 / Day 100 +1 / Day 101 +3 / Day 103 +9 / Day 104 +3）
> Flyway：维持 **V15**（Week 16 无新迁移——观测/日志/限流/编排均未动表结构）
> ADR：0034 → **0037**（本周 +3：ADR 0035 MQTT 下行 command / 0036 Prometheus 观测 / 0037 限流日志节流 + 超时可配）
> 基础设施：compose.yml v1.4 → **v1.6**；Infrastructure-Baseline V1.4 → **V1.6**
> 阶段定位：Phase 5（Day 92-112）第二周——从「数据链路打通」到「完整系统上线 + 工程质量收口」
> 技术债：**TD-005 / TD-034 / TD-036 / TD-037 / TD-038 结清**，TD-035 修正描述仍开放，新增报警合并/去重

---

## 一、本周目标 vs 实际

| 目标（Week 15 §六 计划） | 实际 | 状态 |
| ---------------------- | ---- | :-: |
| Day 99: 完整系统联调（MQTT→业务→报警→AI→通知）+ **真实 ESP32 硬件 6 段闭环 + SSE 端到端** | P1（`d661474`，09-06）：**ADR 0035** MQTT 下行 command 契约 + `MqttCommandGateway` + RELAY_ON Redis 5s 节流 + `esp32-dht-relay.ino` 固件（WiFi→EMQX→telemetry 2s QoS1 + command 订阅吸合 3s 自动复位 + LWT，`RELAY_PIN 5→14` 引脚修正避开 N16R8 PSRAM 35/36/37）+ ESP32 零基础学习路线；P2（10-13，`4156463`）：按实拍板校准的乐高式接线图册（六线分步 + 引脚安全地图）+ **真机 6 段闭环 GO**——静置基线 29°C/52% 40 分钟稳定 → 哈气 100%RH 触发 `OVER_HUMIDITY`×9→RELAY_ON×3 → 热风 68°C 触发 `OVER_TEMP` 持续→RELAY_ON 每 5s → 停止后自动恢复 30°C/49.7%；`.env` 回归 MQTT 三变量 SSOT；226 条测试报警批量 resolved。**SSE 浏览器端到端仍未做**（Day 91 P0-4 遗留顺延，见 §五） | ✅（SSE E2E 除外） |
| Day 100: 系统监控 Actuator + Prometheus | pom 引 `spring-boot-starter-actuator` + `micrometer-registry-prometheus` + **三把业务指标尺**（`mqtt.ingest.messages{device_code,outcome}` / `mqtt.alarm.triggered{alarm_type}` / `mqtt.relay.command{outcome}`，`count()` helper 全降级不阻塞业务）+ compose **v1.5** `prometheus` 2.55.1（抓 `host.docker.internal:8080`，host-gateway 兼容 Linux）+ **ADR 0036**（拉取模式/备选 ELK·Pushgateway·APM/actuator 无 JWT 需网络层限制）+ Baseline V1.5；实测 `/actuator/prometheus` 200、`mqtt_ingest_messages_total{esp32-dht-001,ok}=13`、target up、API 查询返回 20 | ✅（Grafana 面板未做，可选顺延） |
| Day 101: 日志系统 Logback + ELK（可选） | `filter/TraceIdFilter`（16 位短 UUID + MDC + `X-Trace-Id` 透传复用（≤32 字符）+ 响应头回传 + finally 防串号，order=-2 最外层）+ `logback-spring.xml` 三 appender（CONSOLE 高亮 / FILE 按天+100MB 滚动 gzip 归档 14 天 2GB / ERROR_FILE 仅 ERROR 独立 50MB/512MB）+ springProfile test 隔离 + `.gitignore` logs；**ELK 评估结论：本期不启用**（单实例文件滚动+traceId 已满足；触发条件=多实例集中日志/聚合超 grep/第 2 微服务）；8081 临时实例实测透传/生成/多行贯穿/error.log 独立捕获；363/363 | ✅ |
| Day 102: JMeter 压力测试 | `day102-load-test.jmx`（4 线程组串行：登录/读 40rps/写 20rps/突发饱和；每采样 traceId 断言回传）+ `run-day102-loadtest.sh`（健康检查→1 次登录取 token 避 IP 限流→水位记录→非 GUI→JTL+dashboard）+ `analyze-jtl.py` + `performance/README.md`（四条刹车）+ 压测报告；**实测**：读 2415 样本 100% 200 40.3 req/s avg 8.5ms p99 18ms / 写 1208 样本 20.1 req/s avg 22ms p99 175.9ms / 登录 avg 134.6ms（BCrypt 主导）/ 突发 4639 req/s → 仅放行 52.5（429 拒绝率 98.87%）；**Day 101 回归守卫：142,771 样本 traceId 0 失败**；口径三方一致（JMeter 200 数 == DB 新增行 == Micrometer 增量）；**5 项发现 → TD-034~038**；结清 TD-005 | ✅ |
| Day 103: 性能优化（SQL/缓存/线程池） | 改动仅 3 文件：`RateLimitInterceptor`（拒绝日志按窗口节流 + `acquire-timeout-ms` 显式可配默认 500 保持原行为 + Javadoc 固化 Guava 语义）+ `application.yml`（rate.limit.* 三项可覆盖 + 开启 http.server.requests 直方图）+ `RateLimitInterceptorTest` 9 用例；**实测**：拒绝日志 **137,568 → 6 行（−99.996%）**、突发期日志 **23.2MB → 1.67MB（−92.8%）**、`_bucket` 0 → 69，而**行为零变化**（放行 1,575/慢样本 1,505 逐项对齐）；**按响应码拆分 JTL 撤回 Day 102「429 阻塞 500ms」误判**（429 avg 0.9ms/p99 3ms，等待只属被放行的 200）；写路径尾部定位**环境噪声**（2.5× 速率 0 条 >100ms 慢查询，带 `SELECT SLEEP(0.25)` 对照组验证仪器）；**ADR 0037**；372/372 | ✅（「不做 SQL/缓存/连接池优化」本身是结论） |
| Day 104: Docker Compose 完整编排（10+ 服务一键启动） | compose **v1.6**：backend 日志卷 `backend-logs`（Dockerfile 预建 `/app/logs` chown app）/ 独立 `MQTT_CLIENT_ID`（互踢坑位编排级修复）/ `rate.limit.*`+DeepSeek/MCP env 注入 / `BACKEND_HOST_PORT` 可配 / `TZ`；Prometheus **双抓取目标**（宿主机+容器模式并存观测）；`.env.example` 新增 5 非敏感变量；**实测**：容器 backend（8082）health UP + Flyway 14 迁移 + traceId 回传 + 日志落盘 + EMQX 双客户端共存 + Prometheus 双 target up + **`--profile full` 15/15 全 healthy（~75s）**；TD-038 结清（畸形 JSON 400 + 3 用例，容器内实测）；Baseline V1.6；375/375 | ✅ |
| Day 105: 周复盘 | 本文件（Week16.md）+ AGENTS §3 收口 | ✅ |

> 本周 7 天全 ✅（Day 99 的 SSE 浏览器 E2E 与 Day 100 的 Grafana 属可选顺延项，非计划主交付）。
> **Phase 5「完整系统上线」主线全部完成**：Week 15 打通数据链路（遥测进库），Week 16 补齐执行回路
> （继电器下行）、观测三件套（指标/日志/traceId）、性能证据（压测+优化）与交付形态（15 服务一键编排）。

---

## 二、关键收获

### 2.1 从「数据链路」到「物理回路」：一条 RELAY_ON 指令的完整闭环（Day 99）

Week 15 的终点是「遥测数据进 `device_data`」；Week 16 第一天把它补成了**完整物理回路**：

```text
DHT22 采集 → ESP32-S3 固件（2s QoS1）→ EMQX（plc/PLANT_A/esp32-dht-001/telemetry）
  → MqttDeviceDataIngestService（Redis SETNX 幂等 → device_data）
  → AlarmDetector（OVER_TEMP 40°C / OVER_HUMIDITY 阈值）
  → MqttCommandGateway（RELAY_ON，Redis 5s 节流，plc/.../command）
  → ESP32 订阅 command → 继电器吸合 3s 自动复位
```

双报警分支都是**物理触发**实测（不是 mock）：哈气 → 100%RH → 继电器 3s×3 次吸合；热风 → 68°C → 继电器每 5s 跳变；停手 → 自动恢复。这一步之后，**「AI 不再是 demo」之外又多了一条「IoT 不再是模拟器」**。

方法论沉淀（比代码更值钱）：
1. **热风吹传感器 ≠ 高湿度**：RH 随温升而降低（物理规律），热风触发的是 OVER_TEMP；湿度触发正解是哈气（呼气 ~35°C/95%RH 遇冷凝结）。野值判别顺序 = 先看静置基线稳定（接线正确）→ 再看野值有无物理来源 → 最后才怀疑接线/固件。
2. **ADR 0035 下行契约**：上行 telemetry 是「设备→云」，下行 command 是「云→设备」——方向不同，可靠性语义不同（QoS1 + 幂等键 + 5s 节流三件套），不能复用同一个 Topic 命名空间混着用。
3. **引脚安全地图**：ESP32-S3-N16R8 的 GPIO 35/36/37 接 PSRAM，拿来接继电器会神秘失败——图册按实拍板逐脚校准（`RELAY_PIN 5→14`）避免了这一类「代码对但硬件不响应」的隐形坑。

### 2.2 可观测性三件套一次成型：指标（Day 100）→ 日志（Day 101）→ 压测作为消费方（Day 102）

三天的产出互为前提，构成完整闭环：

- **指标**（Day 100）：三把业务尺对准 MQTT 业务语义（ingest 成功/跳过、alarm 类型、relay 发送/失败/节流），不是裸暴露 JVM 指标；`count()` helper 设计成 **registry 缺失/异常全降级不阻塞业务**——观测代码的故障域必须小于被观测的业务。
- **日志**（Day 101）：`TraceIdFilter`（MDC + `X-Trace-Id` 透传复用 + 响应头回传）+ logback 三 appender 滚动归档 + ERROR 独立文件。ELK 明确评估后**不启用**——单实例 + 文件滚动 + traceId grep 已满足，「可选」不等于「要做」，写清触发条件（多实例/聚合超 grep/第 2 微服务）比装一套 ES 更有价值。
- **压测**（Day 102）立刻成为观测能力的**第一个真实消费方**：JMeter 每采样注入 16 位 traceId 并断言原样回传，142,771 样本（含 137,568 个 429）0 失败；JTL traceId → 后端日志行 grep 贯穿实测。**Day 101 的代码在 Day 102 得到了它设计时想要的使用方式**。

### 2.3 数据驱动的自我纠错：Day 102 的误判被 Day 103 自己撤回（本周最重要的方法论事件）

Day 102 从突发饱和组得出「`tryAcquire(500ms)` 阻塞线程，突发 p99 504ms = 超时镜像」的结论。Day 103 给该假设写单测时，实测 `tryAcquire` 在所需等待 > 超时时**立即**返回 false（0ms），与「阻塞 500ms」矛盾——顺藤摸瓜把同一份 JTL 按 `responseCode` 拆开后发现：

| 口径 | 未拆分（Day 102 结论） | 按响应码拆分（真相） |
|------|----------------------|-------------------|
| 429 路径延迟 | （混在整体里）| avg 0.9ms / p99 3ms，**从不阻塞** |
| ~500ms 等待 | 归因给 429 | 全部属于**被放行的 200**（有界背压队列，线程占用上界 ≈ permits×timeout ≈ 25） |
| 突发 p99 | 504ms | 与 10ms 的另一次运行是**假差异**——慢样本同为 1,505 条，占比 1.082% vs 0.972% 跨过 1% 分位线 |

三条可复用的硬规则由此固化：
1. **分位数必须拆维度看，且必须同时报「尾部绝对条数」**（p99 跨运行不可比）；
2. **写测试时的假设也要用数据验证**（单测 0ms 失败是纠错的起点）；
3. 「仪器故障冒充结论」：定位慢查询时 `rm` 掉 MySQL 正在写的 slow.log，得到「0 条慢查询」的**无效结论**——加对照组（`SELECT SLEEP(0.25)` 必须被记到）才证明仪器在工作。**任何「没有发现」的结论，先证明仪器在发现。**

Day 102 报告/日志保留原文痕迹加更正块（不静默改写历史），这条工程记录伦理与 ADR 0017 的治理精神一致。

### 2.4 「拒绝制造优化」：证据不支持的地方一律不做（Day 103）

性能优化日最大的产出是**没有做**的三件事：SQL 改写、加缓存、调连接池。证据链：2.5× 速率（50.2 req/s）写 1,506 次 100% 成功、0 条 >100ms 慢查询（对照组验证过仪器）、Hikari acquire max 8ms / pending 0、GC pause max 27ms，且读组同期 max 也有 136ms → 写路径尾部 = 压测机与被测同机的**环境噪声**。TD-037 据此结清（进一步压缩需移出压测机+分段埋点，非当前债务）。

与之配套，真正修掉的是**铁证级**问题：限流拒绝日志放大器（同强度突发 137,568 行 → 6 行、23.2MB → 1.67MB，且放行数/慢样本逐项一致证明限流策略未变）。**优化日的纪律：先证明问题是真的，再动手；改完必须证明行为没被顺带改坏。**

### 2.5 编排收口 = 能力清单复核法（Day 104）

Day 104 没有写业务代码，而是把 Day 96-103 的每一项能力在**容器网络**里重新验证了一遍：

| 能力 | 来源日 | 容器内验证方式 |
|------|-------|--------------|
| MQTT Listener + 幂等 | Day 96 | EMQX 客户端列表双客户端在线；Flyway 14 迁移校验 |
| 日志体系（traceId + 滚动落盘） | Day 101 | `X-Trace-Id` 原样回传；`/app/logs/industrial-ai-hub.log` 落盘、error.log 0 字节 |
| 指标 + Prometheus | Day 100 | 双 target 全 up，`up{job=container}=1` |
| 限流/节流配置化 | Day 103 | `rate.limit.*` 经 env 注入容器 |
| 异常语义 | Day 104 TD-038 | 畸形 JSON 容器内实测 400 |

15 服务（core 6 + full 9）`--profile full` 一键启动 **15/15 healthy（~75s 收敛）**——「一键启动」不是 compose 写完就算，是每个 healthcheck 从 starting 走到 healthy 的过程。

三个编排级新知识：**① bind-mount 配置内容变更不触发容器重建**（prometheus.yml 更新后必须 `restart`，up 只对比服务定义）；**② compose `${VAR:-}` 空 env 陷阱**——把「存在但为空」的变量注入容器后，Spring `${VAR:50}` 视为已存在解析为空串，容器侧默认必须与 application.yml 对齐；**③ MinIO 2025-09 起 Docker Hub 社区镜像 tag 已下架**（pinned tag `RELEASE.2025-09-07T16-13-09Z` 拉取 denied；本地 `latest` 经 `--version` 核实同版本 retag 完成实测，新环境需 quay.io/离线导入）。

### 2.6 治理纪律的日常化：本周的收口节奏

7 天 7 个分支、7 次 `--no-ff` 合并、0 次直推 main；每日文档四件套（Day 日志 / ADR 或 Baseline / TECH-DEBT / AGENTS §3）无一缺席；每项债务结清都带「结清证据 + 日期」（TD-005/034/036/037/038）。ADR 0017 的分支纪律和 ADR 0015 的密钥 SSOT（Day 99 `.env` 回归）在本周从「规则」变成了「肌肉记忆」。

---

## 三、Week 16 演进全景（Day 99-105）

```text
Day 99   物理闭环日（P1 09-06 / P2 10-13 收口）
  ADR 0035      MQTT 下行 command 契约（QoS1 + Redis 5s 节流）
  固件          esp32-dht-relay.ino（telemetry 2s + command 订阅 + LWT + RELAY_PIN 修正）
  图册          hardware-wiring-guide.html（实拍板校准 + 乐高式六线分步）
  真机验证      静置 29°C/52% 40min → 哈气 100%RH→OVER_HUMIDITY→RELAY×3
               → 热风 68°C→OVER_TEMP→RELAY 每 5s → 自动恢复；device_data 3728 行
Day 100  指标日
  三把业务尺    mqtt.ingest.messages / mqtt.alarm.triggered / mqtt.relay.command（全降级不阻塞）
  compose v1.5  prometheus 2.55.1（抓 host.docker.internal:8080）
  ADR 0036      拉取模式 + 备选否决 + actuator 安全边界
Day 101  日志日
  TraceIdFilter 16 位 UUID + MDC + X-Trace-Id 透传/回传（order=-2 最外层）
  logback       CONSOLE/FILE(100MB+14d+2GB)/ERROR_FILE 三 appender + test 隔离
  ELK 评估      本期不启用（触发条件显式化）；363/363
Day 102  压测日
  JMeter 5.6.3  4 线程组（登录/读 40rps/写 20rps/突发）
  实测          读 40.3req/s p99 18ms · 写 20.1req/s p99 175.9ms · 突发 4639→放行 52.5
  守卫          142,771 样本 traceId 0 失败；口径三方一致
  发现          TD-034~038 + 结清 TD-005
Day 103  优化日
  节流          拒绝日志 137,568→6 行（−99.996%）· 23.2MB→1.67MB（−92.8%）· 行为零变化
  直方图        _bucket 0→69（Prometheus 可算 p95/p99）
  纠错          撤回「429 阻塞 500ms」误判（429 p99=3ms）；写路径=环境噪声（带对照组）
  ADR 0037      日志节流 + 超时可配；372/372
Day 104  编排日
  compose v1.6  backend 日志卷/clientId/env 注入 + Prometheus 双目标 + BACKEND_HOST_PORT
  容器实测      health UP · traceId 回传 · 日志落盘 · 双 MQTT 客户端 · 双 target up · TD-038=400
  一键启动      --profile full 15/15 healthy（~75s）；MinIO Hub 下架发现
  Baseline      V1.6（Backend 入表 + §2.1 Profile 分级）；TD-038 结清；375/375
Day 105  复盘日（本日）
  Week16.md     本文件 + AGENTS §3 收口
```

---

## 四、关键指标

### 4.1 测试矩阵（Week 16 逐日）

| Day | 范围 | 单测总数 | 净增 | 失败 | 备注 |
|-----|------|---------|------|:--:|------|
| Day 99 | P1：command 下行 + 节流（355→359 +4）；P2 无生产代码变更 | **359** | +4 | 0 | 硬件闭环 GO，基线沿用 P1 |
| Day 100 | buildService 4 参重载 + SimpleMeterRegistry 三计数器断言 | **360** | +1 | 0 | ADR 0036 |
| Day 101 | TraceIdFilterTest 3 用例（生成/透传/超长防御） | **363** | +3 | 0 | ELK 不启用 |
| Day 102 | 无生产代码变更（压测脚本/报告） | 363 | 0 | 0 | 142,771 样本零回归 |
| Day 103 | RateLimitInterceptorTest 9 用例（节流/超时语义/路径归一/回退） | **372** | +9 | 0 | ADR 0037 |
| Day 104 | GlobalExceptionHandlerTest 3 用例（畸形 JSON/缺 body/合法不受影响） | **375** | +3 | 0 | compose v1.6 |

> Week 16 累计净增 +20（355 → 375，57 个测试类），全部围绕「可观测/可靠性/异常语义」；
> 前端 build 基线保持 0 errors（Day 104 实测 934ms）。

### 4.2 性能基线（Day 102 压测官方运行 → Day 103 优化复测）

| 指标 | Day 102 | Day 103（同一 JMX 同参数） | 说明 |
|------|---------|--------------------------|------|
| 读吞吐 / avg / p99 | 40.3 req/s / 8.5ms / 18ms | 40.2 req/s / — / 20ms | 100% 200 |
| 写吞吐 / avg / p99 | 20.1 req/s / 22ms / 175.9ms | 20.1 req/s / — / 48.9ms | p99 抖动属环境噪声（TD-037） |
| 登录 avg | 134.6ms | — | BCrypt 主导（读路径 ~16 倍） |
| 突发施压 → 放行 | 4639 → 52.5 req/s（429 率 98.87%） | 放行 1,575 / 慢样本 1,505 **逐项一致** | 限流行为零变化 |
| 拒绝日志行 | 137,568 | **6**（−99.996%） | TD-034 结清 |
| 突发期日志增量 | +23.2MB | **+1.67MB（−92.8%）** | TD-034 结清 |
| `http_server_requests_seconds_bucket` | 0 | **69** | TD-036 结清，Prometheus 可算 p95/p99 |
| traceId 回传断言 | 142,771 样本 0 失败 | （回归守卫沿用） | TD-005 结清 |

### 4.3 ADR 决策记录（Week 16 +3 → 0037）

| ADR | 主题 | Day | 关键决策 |
|-----|------|-----|---------|
| 0035 | MQTT 下行 command 契约 | 99 | 独立 Topic `plc/{site}/{device}/command`；QoS1 + `MqttCommandGateway`；RELAY_ON Redis 5s 节流防继电器抖动 |
| 0036 | Prometheus 指标观测 | 100 | 拉取模式（/actuator/prometheus）；compose v1.5 + host-gateway；备选 ELK/Pushgateway/APM 否决理由；actuator 无 JWT 需网络层限制 |
| 0037 | 限流日志节流 + 取令牌超时可配 | 103 | 拒绝日志按窗口节流（−99.996%）；`rate.limit.acquire-timeout-ms` 显式化（默认 500 = 保持原行为）；Guava tryAcquire 语义固化 |

### 4.4 基础设施（compose v1.4 → v1.6 / Baseline V1.4 → V1.6）

| 版本 | 变化 | Day |
|------|------|-----|
| v1.5 | 新增 `prometheus` 2.55.1（9090，抓宿主机 8080）+ `prometheus/prometheus.yml` + `prometheus-data` volume | 100 |
| v1.6 | backend 日志卷 `backend-logs` + Dockerfile `/app/logs` 预建；独立 `MQTT_CLIENT_ID`；`rate.limit.*`/DeepSeek/MCP env 注入；`BACKEND_HOST_PORT` 可配；`TZ`；Prometheus 双抓取目标（宿主机+容器并存）；profile 分级显式化（core 6 / full 15） | 104 |
| Baseline V1.5 / V1.6 | Service Registry 增 Prometheus / Backend 行；§2.1 Profile 分级表（15/15 实测）；MinIO Hub 下架审计记录 | 100 / 104 |

### 4.5 文档产出（Week 16 新增/修订）

| 文档 | 内容 | Day |
|------|------|-----|
| docs/decision-log/0035 / 0036 / 0037 | 下行契约 / 观测选型 / 日志节流 | 99 / 100 / 103 |
| docs/notes/hardware-wiring-guide/*.html | 实拍板校准接线图册（节点 A/B 六线分步 + 引脚安全地图） | 99 |
| backend/learning/esp32/esp32-dht-relay/ | 固件 + 烧录 README | 99 |
| prometheus/prometheus.yml | 抓取配置（v1.6 起双目标） | 100 / 104 |
| backend/performance/（jmx + 脚本 + 分析器 + README） | JMeter 资产 + 四条刹车 | 102 |
| docs/reports/day102-jmeter-loadtest-report.md（含 Day103 更正块） | 压测报告 + 误判更正 | 102 / 103 |
| docs/reports/day103-performance-optimization-report.md | Before/After + 对照组方法论 | 103 |
| docs/TECH-DEBT.md | TD-005/034/036/037/038 结清入档；TD-035 修正；新增报警合并候选 | 102/103/104 |
| docs/Architecture/Infrastructure-Baseline.md | V1.5 → V1.6 | 100 / 104 |
| compose.yml + .env.example | v1.5 → v1.6 + 编排变量段 | 100 / 104 |
| backend/DAILY/Day99~104.md | 逐日日志 | 99-104 |
| 本文件 Week16.md | Day 99-104 全段复盘 | 105 |

### 4.6 技术债账本（Week 16 结算）

| 编号 | 内容 | 状态 |
|------|------|------|
| TD-005 | 全局日志 traceId | ✅ Day 101 落地 → Day 102 高压复验（142,771 样本 0 失败） |
| TD-034 | 限流拒绝日志放大 | ✅ Day 103（−99.996% 行数 / −92.8% 体积，行为零变化） |
| TD-036 | 缺 HTTP 延迟直方图 | ✅ Day 103（_bucket 0 → 69） |
| TD-037 | 写路径尾部延迟 | ✅ Day 103 定性为环境噪声（证据不支持优化，带对照组） |
| TD-038 | 畸形 JSON 返回 500 | ✅ Day 104（显式 400 + 3 用例 + 容器实测） |
| TD-035 | 限流桶全局单桶 | ⏳ 修正描述（429 从不阻塞证伪 Day 102 怀疑）；桶粒度双层设计需独立 ADR + 安全评审 |
| 新增 | 报警合并/去重（持续超限每 2s 一条 alarm，一次吹风 100+ 条） | ⏳ Day 99 记入，待规则引擎侧设计 |

---

## 五、遗留 & 风险

### 遗留（进入 Week 17 / Phase 5 尾段的候选池）

1. **TD-035 限流桶粒度**：全局 + 客户端双层桶设计，需独立 ADR + 安全评审（唯一在册 P2 债务）；
2. **报警合并/去重**：同设备同类型未恢复报警合并（Day 99 技术债），避免报警风暴污染列表与 AI 摘要；
3. **SSE 真实浏览器 E2E**（Day 91 P0-4 顺延）：SseEmitter 无法被 IT 触发，需 Selenium/Playwright 真实浏览器验证（sendSafely + @PreDestroy + 30min timeout 三重防护已就位）；
4. **节点 B 固件**：ESP32 #2 + DHT22 #2 + BH1750 + OLED（图册已备、`Wire.begin(6,7)` I2C 方案已定）；
5. **Grafana 面板**：Prometheus 数据已有，面板未建（Day 100 可选顺延）；
6. **前端容器化**：compose 编排覆盖基础设施 + backend，前端仍宿主机 dev（nginx 前端服务候选）；
7. **MinIO 镜像源策略**：Hub tag 下架后的固定来源（quay.io / 离线导入）与「是否保留 MinIO」一并决策；
8. **多实例生产化前置**（Day 91 P1-2）：SseEmitterRegistry 进程内 / inspectionQueue / AI 限流 per-JVM / SimpleVectorStore 内存 → Redis pub/sub 桥 + fanout queue + Redis 限流 + Qdrant。

### 风险（Week 17 前瞻）

1. **项目收尾的长尾**：Week 17 是「收尾 + 面试准备」（Day 106-112），任务性质从「写代码」切换为「写文档/录视频/背八股」，容易拖延——建议按 roadmap 逐日硬切换，代码只允许修 bug 不允许加功能；
2. **Tag v3.0-final 的验收口径**：Day 112 打 tag 前需要一次全量回归（375 tests + 前端 build + compose 编排冒烟），建议 Day 111 预演一次，避免最后一天救火；
3. **演示环境依赖**：演示视频/面试演示依赖 EMQX+ESP32 真机（或 PlcSimulator），需准备一条**无硬件也能跑**的降级演示路径（seed 数据 + 模拟器）；
4. **文档-代码一致性**：README/架构文档将迎来大改版（Day 106/108），以 Application-Architecture V2.5 + Baseline V1.6 为 SSOT 改写，避免引入与 ADR 冲突的新表述。

---

## 六、下周计划（Week 17 = Day 106-112，DAILY_ROADMAP「第 16 周：收尾 + 面试准备」）

> Phase 5 收官周：把 112 天的工程成果转化为可展示、可讲述、可交付的形态。

| Day | 任务 | 交付物 |
|-----|------|--------|
| Day 106 | 项目 README 完整版 | 架构图 + 技术栈 + 功能模块 + AI/IoT 亮点 |
| Day 107 | 录制项目演示视频 | 5-8 分钟（含真实硬件闭环片段） |
| Day 108 | 整理项目技术文档 | 设计决策索引 + 踩坑记录汇总 |
| Day 109 | 准备面试项目介绍 | 3 分钟版本 + 10 分钟版本 |
| Day 110 | Java 八股文梳理 | 集合/多线程/JVM/Spring（结合本项目实际用例） |
| Day 111 | 简历重写 | 以 Industrial AI Hub 为核心项目 |
| Day 112 | 最终复盘 | ROADMAP-COMPLETE.md + **Git tag v3.0-final** |

> 建议插队项（不影响主线）：Day 108 前后择机做一次**全量预演**（mvnw test + 前端 build + compose 冒烟），
> 为 Day 112 的 v3.0-final 验收兜底；报警合并/去重与 TD-035 不建议塞进 Week 17（Phase 5 已过主线终点，
> 记入「未来迭代」清单即可）。

---

> Week 16 收官声明：Day 99-104（6 天）交付「物理闭环（真机 6 段 + ADR 0035 下行契约）→ 指标观测
> （三把业务尺 + ADR 0036）→ 链路追踪（traceId + 滚动日志，ELK 明智地说不）→ 压测取证（4639 req/s
> 施压 + 142,771 样本零回归）→ 数据驱动优化（−92.8% 日志放大 + 撤回自己的误判 + 拒绝无证据的优化，
> ADR 0037）→ 一键编排（15/15 healthy + TD-038 结清）」。**Phase 5 的系统主线到此完整**：
> 遥测进得来、报警发得出、指令下得去、指标看得见、日志追得到、性能说得清、环境一键起。
> 测试 375/375 全绿、前端 build 0 errors、5 项技术债结清，基线无回退。
>
> 维护者：AI 助手 + hula0710
