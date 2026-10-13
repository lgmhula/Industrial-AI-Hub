# Decision 0037: 限流拒绝日志节流 + 取令牌超时显式可配（Day 103 性能优化）

| 属性 | 值 |
|------|------|
| **状态** | ✅ 已采纳（Day 103 落地） |
| **决策日期** | 2026-10-13 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | DAILY_ROADMAP Day 103（性能优化）/ ADR 0036（Prometheus 观测）/ `docs/reports/day102-jmeter-loadtest-report.md`（证据来源）/ TD-034、TD-035、TD-036、TD-037 / `RateLimitInterceptor`（Day 37 引入） |

## 1. 背景

Day 102 用 JMeter 对关键接口做了真实并发压测（4,639 req/s 施压），暴露出三个问题，
其中两个需要在 Day 103 收口：

1. **拒绝日志放大（TD-034）**：`RateLimitInterceptor` 每拒绝一次写一行 WARN。
   一次 30s 突发产生 **137,568 行 / +23.2MB**（约 780KB/s）日志。持续洪泛可在数小时内
   写满磁盘——压测本身成了一台"日志放大器"。
2. **观测缺口（TD-036）**：未开 `percentiles-histogram` → `/actuator/prometheus` 只有
   `http_server_requests_seconds_count/sum`（无 `_bucket`），Prometheus 算不出 p95/p99，
   分位数只能靠压测工具原始采样。
3. **一个被证伪的怀疑**：Day 102 报告初版判断 `tryAcquire(500ms)` 会让**被拒请求**
   阻塞线程（"突发 p99 504ms = 超时镜像"）。Day 103 把同一份 JTL **按响应码拆开**后否证：

| 突发组响应码 | 样本 | avg | p99 | >400ms |
|---|---:|---:|---:|---:|
| 429（拒绝） | 137,568 | 0.9ms | **3ms** | **0** |
| 200（放行） | 1,575 | 474ms | 527ms | 1,438 |

   即被拒路径**从不阻塞**；~500ms 等待全部落在**最终被放行**的请求上——这是 Guava
   `tryAcquire(timeout)` 的有界背压队列（所需等待 > timeout 时立即返回 false，不会空等）。
   同时占用的线程数上界 ≈ `permits × timeout` = 50/s × 0.5s ≈ **25**（Tomcat 200）。

## 2. 决策

### 2.1 拒绝日志节流（修复 TD-034）

`RateLimitInterceptor` 保持默认限流策略不变，仅改造拒绝日志：

- 每个参数化路径在 `rate.limit.log-interval-ms`（默认 **5000ms**）窗口内**最多输出一行** WARN；
- 该行**汇总本窗口内被抑制的拒绝次数**，不丢总量信息；
- 窗口内首个拒绝立即出日志（攻击一开始就可见），后续静默；
- `rate.limit.log-interval-ms=0` 关闭节流（回到逐条日志），留给排障期；

### 2.2 取令牌超时显式可配（澄清，不改默认行为）

- 新增 `rate.limit.acquire-timeout-ms`，默认 **500**（与 Day 37 以来行为一致）；
- `0` = 无参 `tryAcquire()`，立即甩掉不排队（延迟可预测，牺牲突发容忍度）；
- 在类 Javadoc / 配置注释 / ADR 中固化 Guava 的真实语义，避免再次误读。

### 2.3 开启 HTTP 延迟直方图（修复 TD-036）

`management.metrics.distribution.percentiles-histogram.http.server.requests: true`
→ Prometheus 产出 `http_server_requests_seconds_bucket`，p95/p99 可在 PromQL 里算。

### 2.4 明确不做的事

- **不改默认限流桶粒度**（仍是 per-path 全局单桶）：改为 per-user/IP 会改变安全语义
  （多 IP 攻击可绕过），且需与全局桶组合成双层设计，属独立议题 → 保留 TD-035 待议；
- **不把 `acquire-timeout-ms` 默认改成 0**：证据证伪了"阻塞"前提，改成 0 只会把本可
  成功的突发请求拒掉，无收益；
- **不动 `AiRateLimitInterceptor`**：其 429 日志同样逐条输出，但 AI 端点限流为 2~5 req/s
  且调用昂贵，不具备同等放大倍率，留作后续统一治理。

## 3. 备选方案与取舍

| 方案 | 否决理由 |
|------|----------|
| 拒绝日志降级为 DEBUG | dev profile 本就 `dev.reboot: DEBUG`（`application-dev.yml`），仍会落盘，等于没治；prod 才有效，覆盖不全 |
| 完全删除拒绝日志 | 丢失"正在被洪泛"的即时可观测性，也丢失 traceId 线索 |
| 只记首条、之后完全不汇总 | 不知道被压制了多少——丢总量信息 |
| 引入独立异步审计日志/采样器 | 对"防日志放大"而言过重，且引入新组件；当前主日志一行/窗口已足够 |
| 默认 `acquire-timeout-ms=0` | 把有界背压队列（可救回约 1% 突发请求）换成硬拒绝，实测证据不支持 |

## 4. 后果

**收益（实测）**

| 维度 | Day 102（改前） | Day 103（改后） | 变化 |
|---|---:|---:|---:|
| 同强度突发产生的 `Rate limit exceeded` 行数 | 137,568 | **6** | −99.996% |
| 同强度突发期间日志增量 | +23.2MB | **+1.67MB** | **−92.8%** |
| `http_server_requests_seconds_bucket` 系列 | 0 | **69** | 可算 p95/p99 |

**行为不变（实测对齐，证明只改了日志/配置未改策略）**

| 指标 | Day 102 | Day 103 |
|---|---:|---:|
| 突发放行数（200） | 1,575 | 1,575 |
| 慢样本（>100ms） | 1,505 | 1,505 |
| 放行样本 avg | 474.1ms | 473.5ms |
| 429 avg / p99 | 0.9ms / 3ms | 0.8ms / 3ms |

**代价与限制**

- 被节流的拒绝请求不再逐条留痕：定位"某一个具体被拒请求"需依赖客户端拿到的
  响应头 `X-Trace-Id`（Day 101 保证 429 也回传）或 Prometheus 的 429 计数；
- 节流状态为**进程内**（`ConcurrentHashMap` per-JVM）——多实例部署时每个副本各自
  按窗口输出，总日志量 ≈ 副本数 × 1 行/窗口，仍远低于逐条；与 Day 91 P1-2 记录的
  "多副本 per-JVM 状态"同源，纳入同一治理项；
- 总量口径以 Prometheus `http_server_requests_seconds_count{status="429"}` 为准。

## 5. 验证（真数据，详见 `docs/reports/day103-performance-optimization-report.md`）

- 后端全量 **372/372**（363 基线 + 9 新增 `RateLimitInterceptorTest`），0 failures；
- 用**同一份 JMX / 同一参数**在 8081 复跑：日志行数、日志增量、直方图三项改善均实测；
- 9 个新单测**固化 Guava 真实语义**（超时不足 → 立即 false；超时够 → 等待后放行），
  防止"429 阻塞"这类误读再次进入结论；
- 写路径 SQL 用**带对照组的** MySQL 慢查询日志（>100ms）核验：30s / 50.2 req/s / 1,506 次
  写入期间 **0 条**慢查询（对照组 `SELECT SLEEP(0.25)` 确认日志机制真的在工作）。

## 6. 后续

- TD-035：限流桶按 userId/IP 细分（双层桶设计）——需要独立 ADR 与安全评审；
- TD-038：`HttpMessageNotReadableException` 目前走兜底返回 500，语义应为 400；
- 若未来需要"逐条拒绝审计"，走独立审计通道（异步 + 采样），而非主应用日志。

---

> 本 ADR 同时是"**聚合分位数会掩盖分组差异**"这一方法论的落地记录：
> 同一份 JTL，不拆维度得出的结论（"429 阻塞 500ms"）与按 `responseCode` 拆开后的结论完全相反。
