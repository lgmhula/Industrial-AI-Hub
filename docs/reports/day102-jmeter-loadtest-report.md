# Day 102 压测报告 — JMeter 关键接口压力测试

> 日期：2026-10-13 | 分支：`feat/day102-jmeter-loadtest` | 工具：Apache JMeter 5.6.3（Corretto 17，非 GUI）
> 观测基线：Day 100 Prometheus（`/actuator/prometheus`）+ Day 101 日志 traceId（`X-Trace-Id` 透传）
> 原始结果：`backend/performance/results/day102-20261013-221412.jtl`（官方运行）+ `...-220924.jtl`（复现运行）

---

## 一、被测环境与方法

| 项 | 值 |
|----|----|
| 目标 | 临时实例 `http://127.0.0.1:8081`（Day 101 代码，`MQTT_ENABLED=false`） |
| 为何用 8081 | 常驻 8080 与临时实例共用 Paho clientId `iah-backend-mqtt`，会 fail-fast（Day 101 踩坑 2）；压测只关心 HTTP 层 |
| 后端运行时 | Temurin JDK 25，Spring Boot 3.5.0，Tomcat 默认线程池 |
| 依赖 | MySQL / Redis / RabbitMQ 容器（EMQX 不参与 HTTP 压测） |
| 压测机 | 同一台 Mac（localhost 回环）→ 结论含被测/施压共置的 CPU 竞争，属**偏保守**估计 |
| 登录限流 | `AuthRateLimitService`：30 次 / 5 分钟 / IP；连续 5 次失败锁 15 分钟 → 脚本只登录 1 次取 token，其余用 `-Jtoken` 注入 |
| 通用限流 | `RateLimitInterceptor`：`rate.limit.permits` 默认 **50 req/s，按 path 分桶**（容器启动未覆写该配置） |

四个线程组串行执行，避免相互干扰。

---

## 二、结果（官方运行 `day102-20261013-221412`）

| 线程组 | 样本 | 施压速率 | 实际吞吐 | 2xx 率 | avg | p50 | p95 | p99 | max |
|--------|-----:|---------|---------:|-------:|----:|----:|----:|----:|----:|
| `00-Login-Latency`（登录） | 5 | 1.5s think | 0.8/s | 100% | **134.6** | 124 | 161.2 | 162.6 | 163 |
| `01-Read-Devices-40rps`（读） | 2,415 | 40 req/s（限速） | 40.3/s | 100% | **8.5** | 8 | 12 | 18 | 42 |
| `02-Write-DeviceData-20rps`（写） | 1,208 | 20 req/s（限速） | 20.1/s | 100% | **22.0** | 17 | 38 | 175.9 | 446 |
| `03-Burst-Saturation`（突发） | 139,143 | 不限速 | 4,639/s 施压 | 1.13% | **6.3** | 1 | 2 | 504 | 573 |

单位：延迟 ms。

**突发组状态码构成**：`200` 1,575 条 / `429` 137,568 条 → **被放行 ≈ 52.5 req/s，拒绝率 98.87%**。

**复现性**（运行 #1 `day102-20261013-220924`）：

| 线程组 | 样本 | 吞吐 | avg | p99 | 状态码 |
|--------|-----:|-----:|----:|----:|--------|
| 读 | 2,414 | 40.3/s | 11.0 | 25.9 | 200:2414 |
| 写 | 1,209 | 20.1/s | 18.4 | 35.0 | 200:1209 |
| 突发 | 147,653 | 4,921/s 施压 | 5.9 | 236.5 | 200:1576 / 429:146077 |

两次运行各线程组吞吐/成功率一致（差异 < 6%），**唯一明显差异是写路径尾部延迟**（p99 35ms → 175.9ms），见发现 F3。

---

## 三、发现

### F1. 限速以下读路径余量充足
40 req/s 稳定压测 60s，2,415 样本 100% 200，p99 18ms、max 42ms。说明 `GET /api/devices` 在限流刹车以内**远未触及瓶颈**。

### F2. 登录耗时是读路径的 ~16 倍（BCrypt 主导）
登录 avg 134.6ms / p50 124ms，而同样经过 JWT 校验的读路径只有 8.5ms。登录链路的成本集中在 BCrypt 校验（`AuthService`）与 JWT 签发，而非数据库。登录吞吐同时被 30 次/5 分钟/IP 策略硬限——**登录是"低频高成本"接口，压测不能靠堆并发**。

### F3. 写路径尾部延迟不稳定（Day 103 待查）
写路径 avg 18.4→22.0ms 稳定，但 p99 从 35ms 涨到 175.9ms、max 446ms（两次运行差异 5 倍）。写入链路含 `DeviceDataMapper` 落库 + 报警规则判定 + `DeviceDataProducer` 广播 + 日志落盘，尾部噪声来源待定位（候选：MySQL 刷盘、RabbitMQ publish、70MB 日志 appender 的 I/O）。

### F4. 真正的天花板是限流器，不是应用处理能力
- 施压 **4,639 req/s**，实际只放行 **52.5 req/s**（≈ 配置的 50 req/s + Guava RateLimiter 初始突发容量），拒绝 137,568 条。
- `RateLimitInterceptor` 的令牌桶**按 path 全局共享**（`limiters.computeIfAbsent(path, ...)`，key 只有 path）→ **单个客户端即可耗尽某路径的全站配额**（队头/饥饿型 DoS，多租户下尤其危险）。
- `limiter.tryAcquire(500, MILLISECONDS)`：被拒请求要**阻塞 Tomcat 工作线程最长 500ms** 才返回 429 → 突发组 p99 = **504ms** 正好是这 500ms 超时的镜像。高并发洪泛时 429 本身会占用线程池，构成放大效应。
- 429 响应体为 `ApiResponse` JSON，响应很快（突发组 p50 = 1ms）。

### F5. 429 拒绝路径存在日志放大（磁盘耗尽风险）
- 137,568 次拒绝（+少量成功请求）使应用日志增长 **+23,198,131 字节（≈23.2MB）**，约 **168 字节/拒绝**。
- 4,639 req/s 施压下日志写入速率约 **780KB/s**；`RateLimitInterceptor` 每拒绝一次写 1 行 WARN。
- 同期 `error.log` **零增长**（拒绝是 WARN 不是 ERROR，LevelFilter 分层正确）——但也意味着 ERROR 文件帮不上这类洪泛。
- 30 秒突发即可产出 23MB 日志，持续攻击可直接写满磁盘（`maxHistory 14 天 / totalSizeCap 2GB` 只能约束归档，不约束当日写入速率）。

### F6. Day 101 traceId 在高压下零回归，且能关联压测请求
142,771 个样本（读 2,415 + 写 1,208 + 登录 5 + 突发 139,143）**traceId 回传断言 0 失败**——包含 137,568 个 429 响应（`TraceIdFilter` order=-2 在限流拦截器之前写响应头）。

JTL（`sample_variables=traceId`）→ 后端日志实测贯通：

| 压测样本 | traceId | 后端日志证据 |
|----------|---------|--------------|
| 读 200 | `fd5df1fc5c2b0356` | `DEBUG ... AuthInterceptor - 权限校验: userId=1, userRoles=[ADMIN]` |
| 写 200 | `8c7858fcd922ac34` | `DEBUG ... AuthInterceptor …` + `DeviceDataProducer - 设备数据已广播: DeviceData{deviceId=1…}` |
| 登录 200 | `a0149e24eaa93328` | `INFO AuthService - 登录成功 username=admin` + `OperationLogAspect - 操作日志: op=LOGIN` |
| 突发 **429** | `4b0c4e99014c9589` | `WARN RateLimitInterceptor - Rate limit exceeded: /api/devices` |

**被限流的压测请求同样可凭 traceId 回查后端日志**（Day 102 计划里想要的关联能力已实测成立）。

### F7. 数据与观测口径三方一致（无脏数据）
| 口径 | 数值 |
|------|------|
| JMeter 写路径 200 数 | 1,208 |
| MySQL `device_data` 压测期新增行（`id > 8073`） | 1,208 |
| Micrometer `http_server_requests_seconds_count{uri="/api/device-data/device/{deviceId}",status="200"}`（本实例累计） | 3,793 = 1,207+1,209+169+1,208（四次试跑之和） |

`alarm` 维持 239（写入 25.0°C 落在规则区间 `(0,40)` 内，无报警噪音）；`operation_log` 仅 +6（= JMeter 5 次 + 脚本 1 次真实登录）。

### F8. Prometheus 侧缺延迟直方图
`/actuator/prometheus` **没有 `http_server_requests_seconds_bucket`**（Day 100 未开 `management.metrics.distribution.percentiles-histogram`）→ Prometheus 只能算 count/sum（平均延迟），**算不出 p95/p99**。本次 p 分位全部来自 JMeter 原始采样。

正面：URI 标签已模板化（`/api/device-data/device/{deviceId}`），**无高基数风险**；429 被记为 `outcome="CLIENT_ERROR", status="429"`，拒绝流量在监控里可见（本次 `/api/devices` 累计 429 = 436,457）。

---

## 四、Day 103 优化候选（按收益/成本排序）

| 优先级 | 措施 | 依据 |
|:---:|------|------|
| P0 | 限流拒绝日志限流：首 N 条 + 采样（或降级 DEBUG），避免每拒绝一行 WARN | F5：30s 突发 → 23MB |
| P0 | 限流判 `tryAcquire()` 快速失败（或把 500ms 降到 0~50ms），避免 429 阻塞线程 | F4：p99 504ms = 超时镜像 |
| P1 | 限流桶改为「用户/IP 维度」（或全局+客户端双桶），防止单客户端耗尽路径配额 | F4：桶 key 只有 path |
| P1 | 开启 `percentiles-histogram` + SLO，把 p95/p99 纳入 Prometheus | F8 |
| P2 | 定位写路径尾部（p99 176ms）：MySQL/RabbitMQ/日志 I/O 分段计时 | F3 |
| P2 | 登录成本治理（BCrypt strength 评估 / 结果缓存），保持安全前提 | F2 |

---

## 五、压测后清理（已执行并核对）

```text
水位数（压测前）      : device_data max(id) = 8073, count = 8057
压测新增              : id > 8073 → 1208 行
DELETE FROM device_data WHERE id > 8073;   -- 1208 行删除
清理后核对            : max(id) = 8073, count = 8057, alarm = 239, 残留 0 行
```

`backend/performance/results/` 为运行时产物，已加入 `.gitignore`（JMX/脚本/README 入库）。

---

> 结论：**Day 102 GO**。限速以下读/写/登录三条链路 100% 成功且延迟可控；系统真正的流量边界是 Day 37 引入的 50 req/s 按路径限流器，
> 它有效挡住了 4.6k req/s 洪泛（98.87% 拒绝），但「全局单桶 + 500ms 阻塞 + 每拒绝一行 WARN」三点构成 Day 103 的明确优化靶子。
> Day 101 的 traceId 体系首次在高并发（含 429）下被验证为端到端可用。
