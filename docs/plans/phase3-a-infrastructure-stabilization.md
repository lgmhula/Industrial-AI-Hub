# Phase 3-A — 基础设施稳定化规划

> **Baseline**: v2.1.0 (commit `ec9a158`, tag `v2.1.0`)  
> **创建日期**: 2026-08-03  
> **状态**: 规划中 — 冻结期间，仅文档，不改代码/配置  
> **前置**: Baseline V2.1 Release Gate GO  

---

## 1. 基线状态

### 1.1 冻结点

| 项 | 值 |
|---|-----|
| Tag | `v2.1.0` (annotated) |
| Commit | `ec9a158` — "fix: complete Maven Wrapper + untrack runtime data from Git" |
| 冻结时间 | 2026-08-03 21:09 (GMT+8) |
| Release Gate | **GO** — 三次审计全部通过 |
| 工作区 | clean |

### 1.2 基础设施大盘

| 服务 | 镜像 | 端口 | 数据挂载 | Healthcheck | 当前状态 |
|------|------|------|---------|-------------|:---:|
| MySQL | mysql:8.4 | 3307:3306 | bind-mount `./mysql/data` | mysqladmin ping | healthy |
| Redis | redis-stack:7.4.0-v1 | 6379, 8001 | bind-mount `./redis/data` | redis-cli ping | healthy |
| RabbitMQ | rabbitmq:4.0-management | 5672, 15672 | bind-mount `./rabbitmq` | port_connectivity | **unhealthy** (cookie 0644) |
| Nacos | nacos-server:v2.4.3 | 8848, 9848 | bind-mount `./nacos` | console health | healthy |
| MinIO | minio:latest | 9000, 9001 | bind-mount `./minio` | health/live | healthy |
| Elasticsearch | elasticsearch:8.17.0 | 9200, 9300 | bind-mount `./elasticsearch` | cluster/health | healthy |
| MySQL Master | mysql:8.4 | 13306:3306 | bind-mount `./mysql/ms-data/master` | mysqladmin ping | healthy |
| MySQL Slave1 | mysql:8.4 | 13307:3306 | bind-mount `./mysql/ms-data/slave1` | mysqladmin ping | healthy |
| MySQL Slave2 | mysql:8.4 | 13308:3306 | bind-mount `./mysql/ms-data/slave2` | mysqladmin ping | healthy |
| Redis Sentinel1 | redis-stack:7.4.0-v1 | 26379 | 无数据卷 | **缺失** | — |
| Redis Sentinel2 | redis-stack:7.4.0-v1 | 26380 | 无数据卷 | **缺失** | — |
| Redis Sentinel3 | redis-stack:7.4.0-v1 | 26381 | 无数据卷 | **缺失** | — |
| Backend | industrial-ai-hub-backend | 8080 | 无 | wget /actuator/health | healthy |

### 1.3 数据目录生命周期现状

| 中间件 | 宿主机目录 | .gitignore 覆盖 | 运行时残留 |
|--------|-----------|:---:|:---:|
| MySQL | `mysql/data/` | 是 | data/ 正确被忽略 |
| Redis | `redis/data/` | 是 | data/ 正确被忽略 |
| RabbitMQ | `rabbitmq/` | 是 | `.erlang.cookie`(0644) + `mnesia/`(38文件) 残留 |
| Nacos | `nacos/` | 是 | `derby-data/` + `protocol/raft/` 残留 |
| MinIO | `minio/` | 是 | `.minio.sys/` 残留 |
| Elasticsearch | `elasticsearch/` | 是 | `_state/` + `nodes` + `node.lock` 残留 |

> **规则**: `.gitignore` 已阻止新增文件进入 Git 跟踪，但已在 V2.1 前产生的残留文件 `git rm --cached` 仅完成了 ES/MinIO/Nacos（B2 修复），RabbitMQ mnesia 遗留至 Phase 3-A。

---

## 2. 已知延期问题（V2.1 审计遗留）

以下问题在 V2.1 最终审计中标记为 "不阻断发布，延后处理"。Phase 3-A 的目标是全部清零。

### 2.1 RabbitMQ cookie/mnesia 治理 [P0]

**现象**:
- 宿主机 `rabbitmq/.erlang.cookie` 权限为 `0644`，RabbitMQ 4.0 要求 `0600`
- bind-mount 下容器内 `chmod` 被宿主机 FS 权限覆盖，Erlang 分布式协议拒绝启动
- 容器进入重启循环，healthcheck 永不通过

**mnesia 数据残留**:
- 路径: `rabbitmq/mnesia/rabbit@12166dfce31d/` — 38 个文件
- 来源: 历史容器实例，节点名 `rabbit@12166dfce31d`
- `.gitignore` 已添加 `rabbitmq/` 阻止新增，但残留文件仍未清理

**影响**: RabbitMQ 在干净 checkout 后 `docker compose up` 无法自行变为 healthy

### 2.2 Redis / ES / MinIO / Nacos 生命周期管理 [P1]

**Redis**:
- `redis/data/` 为 bind-mount，`docker compose down` 不清除
- 3 个 Sentinel 容器无 healthcheck 定义
- Sentinel 的 `depends_on` 仅依赖 `redis`，不等待 `redis` healthy

**Elasticsearch**:
- `elasticsearch/_state/` 和 `elasticsearch/nodes` 为历史实例残留
- `node.lock` 可能在非正常关闭后阻止重启

**MinIO**:
- 使用 `minio:latest` 标签（非固定版本），未来拉取可能引入 breaking changes
- `.minio.sys/` 元数据在 bind-mount 下跨实例可能冲突

**Nacos**:
- 使用嵌入式 Derby 数据库（`derby-data/`），非生产级存储
- `protocol/raft/` 目录残留可能导致集群模式误判

### 2.3 Docker Compose 健康检查完善 [P1]

**缺失 healthcheck 的服务**:
| 服务 | 当前 | 问题 |
|------|:---:|------|
| redis-sentinel1/2/3 | 无 | 无启动状态检测，依赖链不可靠 |
| rabbitmq | 有，但无 `start_period` | RMQ 4.0 启动慢（~30s），`interval: 10s` + `retries: 5` 在启动期内耗尽，标记 unhealthy |

**已有但可改进的 healthcheck**:
| 服务 | 问题 | 建议 |
|------|------|------|
| redis | 无 `start_period` | 加 `start_period: 10s` |
| minio | `curl` 未安装于官方镜像 | 官方镜像基于 scratch/minimal，无 curl。当前 healthcheck 实际依赖容器内 curl — 需验证 |
| mysql-master/slave | `start_period: 30s` 对首次 init 偏短 | 首次初始化（含 `docker-entrypoint-initdb.d` 脚本执行）可能超过 30s |

### 2.4 JWT 生产策略复核 [P0]

**现状**: `JwtUtils` 使用 `System.getenv("JWT_SECRET")` 读取密钥。

**问题**:
1. `application-test.yml` 中 `JWT_SECRET` 是 YAML 属性，不进入 `System.getenv()`，test profile 实际仍走 fallback 硬编码密钥
2. prod 环境下若 `.env` 未设置 `JWT_SECRET`，静默降级为硬编码测试密钥，无 fail-fast
3. `JWT_EXPIRATION_MS` 存在相同问题

**详细方案**: 已在 `docs/plans/phase3-a-infrastructure-stabilization.md` 的 §3.4 中展开。

### 2.5 API 文档同步机制 [P2]

**现状**:
- 人工维护 `docs/Architecture/API-Reference.md`（26 个端点）
- `application-dev.yml` / `application-prod.yml` 中已有 `springdoc.swagger-ui.enabled` 配置
- `pom.xml` 中 **无 springdoc 依赖** — 配置存在但无实际效果
- API-Reference.md 顶部提及 Knife4j (`/doc.html`)，但从未引入

**问题**: 文档与代码不同步（V2.1 审计发现 `GET /api/alarms/all` 仅文档有、代码无）

---

## 3. Phase 3-A 目标

### 3.1 核心目标

> **基础设施可重复启动，`docker compose up/down` 无污染，数据目录生命周期明确，开发环境与生产环境隔离。**

### 3.2 验收标准

| # | 标准 | 验证方式 |
|:---:|------|------|
| G1 | `docker compose up -d` 全部 13 服务在 120s 内达到 healthy | `docker compose ps` 全部 `(healthy)` |
| G2 | `docker compose down` 后无进程残留、无端口占用 | `docker compose ps -a` 为空，`lsof -i :3307,6379,5672,...` 无监听 |
| G3 | `docker compose down -v` 后数据目录可删除重建，第二次 `up` 行为一致 | 两轮 up/down/up 均 healthy |
| G4 | `git status` 在 compose up/down 全周期保持 clean | 运行 compose 前后 `git status` 无变化 |
| G5 | `.gitignore` 覆盖所有运行时数据目录，配置文件（`.conf`/`.cnf`）不受影响 | `git ls-files` 确认无运行时文件被跟踪 |

---

## 4. 任务分解

### T1 — RabbitMQ cookie 与 mnesia 治理

| 属性 | 值 |
|------|-----|
| **风险等级** | P0 — 阻断 compose up |
| **修改范围** | `compose.yml` (1 处), `rabbitmq/rabbitmq.conf` (新增), `rabbitmq/.erlang.cookie` (删除), `rabbitmq/mnesia/` (删除) |
| **依赖** | 无 |
| **预计影响文件** | 3 文件 (1 修改, 1 新增, 2 删除) |

**方案**:
1. 删除宿主机残留的 `rabbitmq/mnesia/` 和 `rabbitmq/.erlang.cookie`
2. 创建 `rabbitmq/rabbitmq.conf`，显式锁定节点名: `nodename = rabbit@localhost`
3. `compose.yml` 中 RabbitMQ volumes 改为:
   ```yaml
   volumes:
     - ./rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro
     - rabbitmq-data:/var/lib/rabbitmq       # named volume，非 bind-mount
   ```
4. 顶级 volumes 声明 `rabbitmq-data:`
5. healthcheck 添加 `start_period: 30s`

**验证方式**:
- `docker compose up -d rabbitmq && sleep 45 && docker compose ps rabbitmq` → `(healthy)`
- `docker compose down -v && docker compose up -d rabbitmq` → 第二次启动也 healthy
- `ls -la rabbitmq/` → 无 mnesia/ 目录，无 .erlang.cookie

---

### T2 — Redis / ES / MinIO / Nacos 数据生命周期治理

| 属性 | 值 |
|------|-----|
| **风险等级** | P1 — 不影响当前运行，但 clone 者会遇到脏状态 |
| **修改范围** | `.gitignore` (复核), 残留运行时数据 (删除), `compose.yml` (可选 named volume 迁移) |
| **依赖** | 无 |
| **预计影响文件** | 0-4 文件 (取决于迁移策略) |

**方案**:

**T2.1 — 清理残留运行时数据**:
```bash
# Elasticsearch
rm -rf elasticsearch/_state elasticsearch/nodes elasticsearch/node.lock elasticsearch/snapshot_cache
# MinIO
rm -rf minio/.minio.sys
# Nacos
rm -rf nacos/derby-data nacos/protocol/raft nacos/naming nacos/connection nacos/tps
# Redis (保留配置，仅清理 data)
rm -rf redis/data/*
```

**T2.2 — .gitignore 复核**:
当前 `.gitignore` 已包含 `elasticsearch/`, `minio/`, `nacos/`, `rabbitmq/`, `redis/data/`, `mysql/data/`，规则正确。复核后确认无遗漏。

**T2.3 — named volume 迁移（可选，Phase 3-B）**:
绑定挂载适合开发环境（可直接查看数据文件），named volume 适合 CI/生产（权限隔离）。Phase 3-A 保持 bind-mount，Phase 3-B 评估是否切换。

**验证方式**:
- 清理后 `git status` clean
- `docker compose down && docker compose up -d` → 全部 healthy
- 各中间件数据目录在 compose up 后重新生成，compose down 后保留（符合开发预期）

---

### T3 — Docker Compose 健康检查完善

| 属性 | 值 |
|------|-----|
| **风险等级** | P1 — 当前不影响运行，但依赖链不可靠 |
| **修改范围** | `compose.yml` (8-10 处修改) |
| **依赖** | T1 (RabbitMQ start_period 依赖 cookie 修复) |
| **预计影响文件** | 1 文件 |

**方案**:

**T3.1 — 补充缺失的 healthcheck**:
```yaml
# redis-sentinel1/2/3 — 三个 Sentinel 容器
healthcheck:
  test: ["CMD", "redis-cli", "-p", "26379", "ping"]
  interval: 10s
  timeout: 5s
  retries: 3
  start_period: 10s
```

**T3.2 — 修正已有 healthcheck 的时间窗口**:

| 服务 | 当前 | 修正后 | 理由 |
|------|------|------|------|
| rabbitmq | 无 start_period | `start_period: 30s` | RMQ 4.0 启动 ~25s |
| redis | 无 start_period | `start_period: 10s` | Redis 启动快，10s 充裕 |
| mysql-master | `start_period: 30s` | `start_period: 45s` | 首次 init 含 initdb 脚本 |
| mysql-slave1/2 | `start_period: 30s` | `start_period: 45s` | 同上 |
| minio | `curl` 依赖未验证 | `test: ["CMD", "mc", "ready", "local"]` 或保留 curl | 需先验证 minio 镜像是否内置 curl/wget |

**T3.3 — depends_on 条件强化**:
```yaml
# redis-sentinel 的 depends_on 加入 condition
depends_on:
  redis:
    condition: service_healthy    # 已有，确认生效
```

**验证方式**:
- `docker compose up -d` 全部服务
- `docker compose ps` 输出所有 13 服务 `(healthy)` — 特别注意 redis-sentinel1/2/3 不再显示 `(unhealthy)` 或空白
- `docker events --filter 'event=health_status'` 确认无 health_status: unhealthy 事件

---

### T4 — JWT 生产策略复核

| 属性 | 值 |
|------|-----|
| **风险等级** | P0 — 安全基线 |
| **修改范围** | `JwtUtils.java` (重构), `JwtConfig.java` (新增), `application*.yml` (3 文件), `AuthService.java` (调用方适配), `JwtAuthFilter.java` (调用方适配) |
| **依赖** | 无 |
| **预计影响文件** | 7 文件 |

**方案**:

**T4.1 — JwtUtils static → instance**:
- 移除 `static` 块和所有 `static` 方法
- 构造器接收 `(String secret, long expirationMs)`
- 由 `@Bean` 方法注入

**T4.2 — 新增 JwtConfig（profile-aware 密钥策略）**:

| Profile | 密钥来源 | 无密钥行为 |
|---------|---------|-----------|
| `test` | `application-test.yml` → `jwt.secret` | 使用 YAML 中写死的测试密钥 |
| `dev` | 环境变量 `JWT_SECRET` | WARN + fallback 测试密钥（本地开发友好） |
| `prod` | 环境变量 `JWT_SECRET`（compose `.env` 注入） | **抛 IllegalStateException，拒绝启动** |

**T4.3 — application.yml 属性补充**:
```yaml
jwt:
  secret: ${JWT_SECRET:}
  expiration-ms: ${JWT_EXPIRATION_MS:86400000}
```

**T4.4 — application-test.yml 密钥修复**:
将单行 `JWT_SECRET: test-secret-...` 改为 `jwt.secret: test-secret-...`，使 `@Value("${jwt.secret}")` 在 test profile 下正确解析。

**验证方式**:
- `SPRING_PROFILES_ACTIVE=test ./mvnw test` → ApplicationContextLoadTest PASS
- `SPRING_PROFILES_ACTIVE=dev JWT_SECRET= ./mvnw spring-boot:run` → 启动 + WARN 日志
- `SPRING_PROFILES_ACTIVE=prod JWT_SECRET= ./mvnw spring-boot:run` → **启动失败**，日志含 `IllegalStateException: JWT_SECRET must be set in production`
- `docker compose up backend` → 正常启动（compose 已传 `JWT_SECRET` 环境变量）

---

### T5 — API 文档同步机制

| 属性 | 值 |
|------|-----|
| **风险等级** | P2 — 不影响功能，文档滞后 |
| **修改范围** | `pom.xml` (1 行), `OpenApiConfig.java` (新增), `API-Reference.md` (添加废弃声明) |
| **依赖** | 无 |
| **预计影响文件** | 4 文件 |

**方案**:

1. `pom.xml` 添加:
   ```xml
   <dependency>
       <groupId>org.springdoc</groupId>
       <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
       <version>2.7.0</version>
   </dependency>
   ```

2. 新增 `OpenApiConfig.java` — 定制 API 文档标题/版本信息

3. `API-Reference.md` 顶部添加废弃声明:
   ```markdown
   > **⚠️ 此文档已被 Springdoc OpenAPI 替代（Phase 3-A）。**
   > 开发环境: http://localhost:8080/swagger-ui.html
   ```

4. Controller 渐进式添加 `@Tag` / `@Operation` 注解（Phase 3-A 仅做示范）

**验证方式**:
- `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` → 访问 `http://localhost:8080/swagger-ui.html` 可浏览全部 26 个端点
- `SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run` → 访问 `/swagger-ui.html` 和 `/v3/api-docs` 均返回 404（prod profile 已关闭）

---

### T6 — Docker Compose profiles 分离（可选增强）

| 属性 | 值 |
|------|-----|
| **风险等级** | P2 — 体验改善，非阻塞 |
| **修改范围** | `compose.yml` (添加 profiles 标记), `README.md` (更新启动指令) |
| **依赖** | 无 |
| **预计影响文件** | 2 文件 |

**方案**: 为重型服务添加 `profiles:` 标记，实现分层启动:
```yaml
# 核心服务（默认启动）
services:
  mysql:         # 无 profiles → 默认启动
  redis:         # 无 profiles → 默认启动
  rabbitmq:      # 无 profiles → 默认启动
  backend:       # 无 profiles → 默认启动

# 按需启动
  nacos:         profiles: ["full"]
  minio:         profiles: ["full"]
  elasticsearch: profiles: ["full"]
  mysql-master:  profiles: ["full"]
  mysql-slave1:  profiles: ["full"]
  mysql-slave2:  profiles: ["full"]
  redis-sentinel1/2/3: profiles: ["full"]
```

启动命令:
- `docker compose up -d` → 6 核心服务 (MySQL + Redis + RabbitMQ + Backend)
- `docker compose --profile full up -d` → 全部 13 服务

**验证方式**:
- `docker compose up -d` → 仅 6 服务 running
- `docker compose --profile full up -d` → 全部 13 服务 running

---

## 5. 执行顺序与依赖关系

```
Phase 3-A 执行拓扑:

T1 (RabbitMQ) ─────────────────────┐
                                    ├──→ T3 (Healthcheck) ──→ T6 (Profiles)
T2 (Data Lifecycle) ───────────────┘
                                    
T4 (JWT Security) ──→ 独立执行

T5 (API Docs)     ──→ 独立执行
```

| 轮次 | 任务 | 理由 |
|:---:|------|------|
| Round 1 | T1 + T2 并行 | 两者都只涉及 compose 和数据目录，无代码变更 |
| Round 2 | T3 | 依赖 T1 的 RabbitMQ cookie 修复（healthcheck 才能通过） |
| Round 3 | T4 | 独立执行，涉及 Java 代码重构，需要单独验证 |
| Round 4 | T5 | 独立执行 |
| Round 5 | T6 | 依赖 T3 完成后 compose 状态稳定 |

---

## 6. 影响范围总览

| 任务 | compose.yml | .gitignore | Java 代码 | YAML 配置 | 配置文件(conf/cnf) | 新增文件 | 删除(残留数据) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| T1 | ✓ | — | — | — | `rabbitmq.conf` | 1 | mnesia + cookie |
| T2 | — | 复核 | — | — | — | 0 | ES/Nacos/MinIO 残留 |
| T3 | ✓ | — | — | — | — | 0 | — |
| T4 | — | — | JwtUtils, JwtConfig, AuthService, JwtAuthFilter | application*.yml × 3 | — | 1 (JwtConfig) | — |
| T5 | — | — | OpenApiConfig | — | — | 1 (OpenApiConfig) | — |
| T6 | ✓ | — | — | — | — | 0 | — |

---

## 7. 风险与回滚

| 任务 | 最大风险 | 回滚方式 |
|:---:|------|------|
| T1 | named volume 在 macOS Docker Desktop 下行为异常 | 回退为 bind-mount + cookie 权限预置 0600 |
| T2 | 误删 redis/data 导致持久化数据丢失 | data/ 仅测试数据，重建成本低；可先 `tar czf` 备份 |
| T3 | Sentinels healthcheck 命令错误导致 healthy 假阳性 | healthcheck 失败反而暴露真实问题，优于无检测 |
| T4 | JwtUtils static→instance 链式影响超出预期 | `git revert`，回退至 static 版本，仅 2 处调用方 |
| T5 | springdoc 版本与 Spring Boot 3.5 不兼容 | 降级 springdoc 版本或回退至纯文档维护 |
| T6 | profiles 分离后发现 compose 启动顺序依赖跨 profile | profiles 是纯标记，不改变启动顺序 |

---

> **Phase 3-A 目标声明**: 完成上述 T1-T6 后，项目达到"基础设施可重复启动"基线，可安全进入 Phase 3-B（中间件功能集成：Redis 缓存、RabbitMQ 消息、Elasticsearch 搜索）。
