# Day 102 — JMeter 压力测试

> Phase 5 Week 16 第 4 天 | JMeter 5.6.3（本机 `~/tools/apache-jmeter-5.6.3`，非仓库内）
> 观测基线：Day 100 Prometheus 指标（`/actuator/prometheus`）+ Day 101 日志 traceId（`X-Trace-Id` 透传）

## 一、为什么这样设计（先读这节再改参数）

压测前必须先知道被测系统**主动挂了哪些刹车**，否则测出来的不是性能而是刹车片：

| 机制 | 位置 | 阈值 | 对压测的影响 |
|------|------|------|--------------|
| 通用限流 | `RateLimitInterceptor`（Guava RateLimiter，**按 path 分桶**） | `rate.limit.permits` 默认 **50 req/s** | 超过即 429；读路径故意压到 40 req/s 才测得到「无刹车」延迟 |
| 登录 IP 限流 | `AuthRateLimitService`（Redis ZSET 滑动窗口） | **30 次 / 5 分钟 / IP** | 登录只能测极少量；主压测用外部注入 token |
| 登录失败锁定 | `AuthRateLimitService` + `user.failed_attempts` | 连续 **5 次失败** → 锁 15 分钟 | 压测必须用**正确密码**，否则把 admin 锁死 |
| 报警规则 | `AlarmRuleConfig` | TEMPERATURE 正常区间 `(0, 40)` | 写入用 `25.0` 落在安全区间 → 不产生报警噪音 |

## 二、测试计划（`jmeter/day102-load-test.jmx`）

四个线程组，串行执行（`serialize_threadgroups=true`）：

| 组 | 线程 | 时长 | 速率 | 目标 |
|----|------|------|------|------|
| `00-Login-Latency` | 1 | 5 循环 | 1.5s think | `POST /api/auth/login` 登录链路延迟（含 BCrypt + JWT 签发） |
| `01-Read-Devices-40rps` | 20 | 60s | **40 req/s**（2400/min） | `GET /api/devices?page=1&size=10` **刹车以下**的干净读延迟 |
| `02-Write-DeviceData-20rps` | 10 | 60s | **20 req/s**（1200/min） | `POST /api/device-data/device/1` 写入链路（含幂等/落库/分发） |
| `03-Burst-Saturation` | 30 | 30s | **不限速** | 冲垮 50 req/s 限流 → 量化 429 比例（验证限流器真的在挡） |

### Day 101 联动断言（每个采样器都有）

1. **traceId 原样回传**：请求头 `X-Trace-Id: ${__RandomString(16,abcdef0123456789,traceId)}`（16 位十六进制，≤32 字符上限）→ 断言响应头 `X-Trace-Id: ${traceId}` 一致；
   若 `TraceIdFilter` 回归（生成新 id 或漏回传），断言立刻失败——**压测同时充当 Day 101 的回归守卫**。
2. **429 也必须有 traceId**：`TraceIdFilter` order=-2 在最外层，限流拦截器抛 429 之前响应头已写入 → 限流响应同样可凭 traceId 回查日志。
3. `03` 组响应码断言为 **Matches 正则 `(200|429)`**（不是 `Equals` + `Or`，见踩坑 1）。
4. 脚本以 `-Jsample_variables=traceId` 让每条采样在 JTL 里带上 traceId → 压测结果可直接 `grep` 后端日志定位（见报告 §F6）。

### 踩坑（改脚本/断言前必读）

| # | 坑 | 现象 | 正确做法 |
|:--:|----|------|----------|
| 1 | `ResponseAssertion` 的 `test_type=72`（Equals\|Or）+ 两个 pattern | **200 响应也判失败**（实测：突发组 1,576 个 200 全被标记失败，`failureMessage` = 自定义消息） | 用 `test_type=1`（Matches）+ 正则 `(200\|429)`，语义明确且 200/429 均实测通过 |
| 2 | JMeter 把 4xx/5xx 默认记为 `success=false` | 即使断言通过，429 仍计入 summariser 的 `Err`（突发组 Err 99%+ 是**预期**，不是应用错误） | 429 比例从 JTL 的 `responseCode` 统计（`scripts/analyze-jtl.py`），不要读 `Err` 列 |
| 3 | `ConstantThroughputTimer` 的 `doubleProp` 必须字面数字 | 写 `${readThroughput}` → XStream `NumberFormatException`，计划直接加载失败 | 字面写 `2400.0`；改速率由脚本 sed 生成临时副本（`run-day102-loadtest.sh`） |
| 4 | UDV 变量名与 `-J` 属性同名 | `${readDuration}` 解析到 UDV，`-JDuration=` 静默失效（时长覆盖不生效） | 时长字段用 `${__P(readDuration,60)}`，并删掉同名 UDV |
| 5 | JMeter 5.6.3 对 JDK 25 有 Unsafe 弃用告警 | 能跑但不保证 | 脚本默认挑本机 JDK 17（`JMETER_JAVA_HOME` 可覆盖） |

非 GUI 运行，`-l` 落 JTL + `-e -o` 出 HTML dashboard。

## 三、怎么跑

```bash
# 1. 目标实例（建议 8081 临时实例，MQTT 关掉避免与常驻 8080 抢 Paho clientId）
cd backend
MQTT_ENABLED=false SERVER_PORT=8081 ./mvnw spring-boot:run

# 2. 压测（默认 40rps 读 / 20rps 写 / 30s 突发）
./performance/scripts/run-day102-loadtest.sh

# 3. 指向别的目标 / 改速率
BASE_URL=http://127.0.0.1:8080 ./performance/scripts/run-day102-loadtest.sh
READ_THROUGHPUT=6000 READ_DURATION=30 ./performance/scripts/run-day102-loadtest.sh
```

要求：`~/tools/apache-jmeter-5.6.3`（或 `JMETER_HOME=...`）；脚本优先用 **JDK 17** 跑 JMeter（5.6.3 官方支持到 17/21，本机 JDK 25 不保证）。

产物（`results/` 已 gitignore，仅本地留档）：

```
results/day102-<ts>.jtl                     原始采样（每请求一行）
results/day102-<ts>-report/index.html       JMeter HTML dashboard
results/day102-<ts>-watermark.txt           压测前 device_data max(id)
results/day102-<ts>-jmeter.log              JMeter 运行日志
```

## 四、压测后清理（写路径会真实落库）

写路径按 20 req/s × 60s ≈ 1200 行真实写入 `device_data`。用压测前水位精确回滚（只删压测产生的行）：

```bash
BASELINE=$(cat results/day102-<ts>-watermark.txt)
docker exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "USE reboot; DELETE FROM device_data WHERE id > ${BASELINE};"
```

清理前建议先核对行数与压测写入量一致；`alarm` 表**不应**因本次压测新增（TEMPERATURE=25.0 在安全区间）。

## 五、观测入口

```bash
# 压测前后对比业务指标（Day 100 三把尺 + HTTP/JVM）
curl -s http://127.0.0.1:8081/actuator/prometheus | grep -E '^(http_server_requests|mqtt_)' | head

# 用 traceId 回查压测请求在后端的完整日志链路
grep '<某个 X-Trace-Id>' backend/logs/industrial-ai-hub.log
```

> 注：Prometheus（compose v1.5）抓的是 `host.docker.internal:8080`；若压测跑在 8081，Prometheus 不覆盖该实例，
> 请直接用实例自身 `/actuator/prometheus` 取数（同源指标，口径一致）。
