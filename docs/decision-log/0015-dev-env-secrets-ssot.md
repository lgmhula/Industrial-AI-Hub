# ADR 0015 — 开发环境密钥来源一致性（.env 唯一事实源）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-16 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | AGENTS.md §8 / docs/SETUP.md §2 / Infrastructure-Baseline §1 |

---

## 1. 背景

### 1.1 事故（2026-08-16）

IDEA 中直接运行 `IndustrialAiHubApplication` 启动失败：

```
Caused by: org.redisson.client.RedisWrongPasswordException: WRONGPASS
```

排查结论：

1. 项目根目录 `.env` 是开发环境敏感配置的事实来源，但 **IDEA 不会自动读取 `.env`**。
2. IDEA Run Configuration（`.idea/workspace.xml`）中存在**历史手工环境变量快照**，
   只包含 `MYSQL_PASSWORD` / `JWT_SECRET` / `JWT_EXPIRATION_MS`，**缺少 `REDIS_PASSWORD`**。
3. `application.yml` 中 `spring.data.redis.password: ${REDIS_PASSWORD:}` 在变量缺失时
   **静默退化为空字符串**，Redisson 以空密码 AUTH → Redis 返回 WRONGPASS。
4. 更严重的漂移已存在：IDEA 快照中的 `JWT_SECRET`（`DevOnly-DefaultKey-...`）与
   `.env` 中的真实密钥**不一致**——同一密钥系统内同时存在两个"真值"，
   轮换 `.env` 后 IDEA 仍使用旧密钥，旧密钥泄露窗口无限拉长。

### 1.2 根本原因

`.env` 只是配置文件，不是加载机制。项目有 3 个消费方，读取方式各不相同：

| 消费方 | 读取机制 | 是否自动 |
|--------|----------|:---:|
| Docker Compose | 自动读取根目录 `.env` 做 `${VAR}` 插值 | ✅ |
| 命令行 JVM | 手动 `source .env` 注入 | ❌ |
| IDEA Run Configuration | 不读 `.env`，只读手工快照 | ❌ |

`${VAR:}` 默认值语法掩盖了变量缺失，错误从"启动时"推迟到"使用时"。

---

## 2. 决策

### 2.1 事实源

> **项目根目录 `.env` 是本地开发环境敏感配置的唯一事实源。**

### 2.2 加载机制（三消费方统一）

| 环境 | 机制 |
|------|------|
| **dev（IDEA / 命令行）** | `application-dev.yml` 声明 `spring.config.import: optional:file:../.env[.properties]`，Spring Boot 3.5 启动时原生读取根 `.env`。IDEA 与命令行行为完全一致，**无需任何手工环境变量**。 |
| **test** | `application-test.yml` 使用隔离占位密钥（`test-secret-*` / `test-password-*`），**不读取本地 `.env`**。 |
| **prod（Docker）** | `compose.yml` `environment:` 块经 `${VAR}` 插值注入容器环境，**不依赖本地 `.env`**。 |
| **Docker Compose 本身** | Compose 自动读取根目录 `.env`（既有机制，保持不变）。 |

### 2.3 密钥优先级

```
dev : OS 环境变量 > .env > application.yml 默认值
docker : .env → Docker Compose → Container Environment
prod : 不依赖开发环境 .env（由部署环境注入）
```

### 2.4 fail-fast

以下敏感变量**不再提供默认值**（取消 `${VAR:}` 空默认），缺失即启动失败：

- `JWT_SECRET`
- `REDIS_PASSWORD`
- `MYSQL_PASSWORD`

非敏感变量（host / port / user / expiration-ms 等）保留默认值。

### 2.5 禁止事项

- ❌ 将真实密钥提交 Git（`.env` 已在 `.gitignore`）。
- ❌ 在 IDEA Run Configuration / 任何 IDE 配置中手工复制密钥。
- ❌ 在 `application*.yml` 中硬编码密钥。
- ❌ 在 `.env.example` 中放置真实密钥（只保留变量名与说明）。

---

## 3. 备选方案（未采纳）

| 方案 | 说明 | 未采纳原因 |
|------|------|------------|
| IDEA EnvFile 插件 | Run Configuration 绑定 `.env` | 依赖插件、仅修 IDEA 单点；命令行仍需 `source`；SSOT 未收敛 |
| Nacos 配置中心 | 密钥收敛至 Nacos | 超出当前路线（ADR 0003：Nacos 预留不启动）；生产架构不变 |
| 继续手填 Run Configuration | 维持现状 | 本次事故的根源，已证明会漂移 |

---

## 4. 影响与验证

- 配置修改：`application.yml` / `application-dev.yml` / `application-test.yml` / `.idea/workspace.xml`（清空快照）。
- 验证：`spring.config.import` 读取 `.env` 已通过 4 项实证测试（读取、覆盖优先级、占位符解析、optional 容错）；
  完整启动验证见本次修复报告（compose 插值 / Redis AUTH / backend health / 无密钥残留扫描）。
- 回归风险：所有 `@SpringBootTest` 依赖 test profile 隔离密钥，已通过 `ApplicationContextLoadTest` 冒烟验证。
