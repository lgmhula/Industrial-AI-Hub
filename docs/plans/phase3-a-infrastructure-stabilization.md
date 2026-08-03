# Phase 3-A — 基础设施稳定化规划

> **Baseline**: v2.1.0 (commit `ec9a158`, tag `v2.1.0`)
> **创建日期**: 2026-08-03 | **审计修订**: 2026-08-03
> **状态**: 规划中 — 冻结期间，仅文档，不改代码/配置
> **前置**: Baseline V2.1 Release Gate GO
> **分支**: `codex/phase-3a-infra-stabilization`（从 `v2.1.0` tag 切出）

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

| 中间件 | 宿主机目录 | .gitignore 覆盖 | git 跟踪文件数 | 运行时残留 |
|--------|-----------|:---:|:---:|:---:|
| MySQL | `mysql/data/` | 是 | 0 | data/ 正确被忽略 |
| Redis | `redis/data/` | 是 | 0 | data/ 正确被忽略 |
| RabbitMQ | `rabbitmq/` | 是 | **38** | `.erlang.cookie`(0644) + `mnesia/` 全部被跟踪 |
| Nacos | `nacos/` | 是 | 0 | `derby-data/` + `protocol/raft/` 已解除跟踪(磁盘残留) |
| MinIO | `minio/` | 是 | 0 | `.minio.sys/` 已解除跟踪(磁盘残留) |
| Elasticsearch | `elasticsearch/` | 是 | 0 | `_state/` + `nodes` + `node.lock` 已解除跟踪(磁盘残留) |

> **关键差异**: V2.1 B2 修复通过 `git rm --cached` 解除了 ES/MinIO/Nacos 的 git 跟踪，磁盘上的残留文件被 `.gitignore` 阻止重新加入。RabbitMQ 的 38 个文件**仍在 git index 中**——`.gitignore` 只阻止新文件加入，不影响已跟踪文件。Phase 3-A 必须通过 `git rm --cached` 解除 RabbitMQ 的 git 跟踪。

> **RabbitMQ .gitignore 分层规则设计**:
> ```
> # 配置文件 — 明确纳入版本控制（通过 ! 例外规则）
> !rabbitmq/rabbitmq.conf
> !rabbitmq/enabled_plugins
> !rabbitmq/definitions.json
> 
> # 运行时数据 — 排除
> rabbitmq/mnesia/
> rabbitmq/.erlang.cookie
> ```
> 设计原则：配置文件 = Git 管理（`rabbitmq.conf` 等），运行时数据 = Git 忽略（`mnesia/`、`.erlang.cookie`）。Fresh clone 必须能直接获取 `rabbitmq.conf`，但不应携带任何历史实例的 mnesia 数据。

---

## 2. 已知延期问题（V2.1 审计遗留）

以下问题在 V2.1 最终审计中标记为 "不阻断发布，延后处理"。Phase 3-A 的目标是全部清零。

### 2.1 RabbitMQ cookie/mnesia 治理 [P0]

**双重问题 — git 跟踪 + 运行时权限**:

1. **git 跟踪层**: 38 个 mnesia 运行时文件 + `.erlang.cookie` 仍在 git index 中。V2.1 B2 修复遗漏了 RabbitMQ（仅处理了 ES/MinIO/Nacos），因为当时认为 `.gitignore` 的 `rabbitmq/` 规则已足够。实际上 `.gitignore` 只阻止**新文件**被跟踪，已跟踪文件不受影响。

2. **运行时权限层**: 宿主机 `rabbitmq/.erlang.cookie` 权限为 `0644`，RabbitMQ 4.0 要求 `0600`。bind-mount 下容器内 `chmod` 被宿主机 FS 权限覆盖，Erlang 分布式协议拒绝启动，容器进入重启循环。

**修复必须同时处理两层** — 先解 git 跟踪，再修运行时权限：

```
层1 (git):  git rm --cached rabbitmq/.erlang.cookie rabbitmq/mnesia/
            → 38 文件从 git index 移除，.gitignore 阻止重新加入
层2 (disk): rm -rf rabbitmq/.erlang.cookie rabbitmq/mnesia/
            → 清理残留，让 RMQ 在干净目录自举
层3 (perm): compose.yml 切换为 named volume
            → cookie 权限由容器管理，不再受宿主机 FS 约束
```

**影响**: 干净 checkout 后 `docker compose up rabbitmq` 无法变为 healthy，且 `git status` 在 compose up/down 周期内不干净。

### 2.2 Redis / ES / MinIO / Nacos 生命周期管理 [P1]

**Redis**:
- `redis/data/` 为 bind-mount，`docker compose down` 不清除
- 3 个 Sentinel 容器无 healthcheck 定义
- Sentinel 的 `depends_on` 仅依赖 `redis`，不等待 `redis` healthy

**Elasticsearch**:
- `elasticsearch/_state/` 和 `elasticsearch/nodes` 为历史实例残留
- `node.lock` 可能在非正常关闭后阻止重启

**MinIO**:
- 使用 `minio:latest` 标签（非固定版本）→ 需锁定为 `minio:RELEASE.2025-09-07T16-13-09Z`
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
| **风险等级** | P0 — 阻断 compose up + git 跟踪污染 |
| **修改范围** | `compose.yml` (1 处), `rabbitmq/rabbitmq.conf` (新增), `rabbitmq/.erlang.cookie` + `rabbitmq/mnesia/` (git rm --cached + 磁盘删除) |
| **依赖** | 无 |
| **预计影响文件** | compose.yml (修改), rabbitmq/rabbitmq.conf (新增), git index 38 entries 移除, 磁盘残留清理 |

**方案**:

**Step 1 — 解除 git 跟踪**（必须先于任何 compose 操作）:
```bash

**前置检查 — 运行态隔离**（阻断条件 — 必须先执行）:
```bash
# 必须确保 RabbitMQ 容器已停止且已移除
docker compose stop rabbitmq
docker compose rm -f rabbitmq

# 确认容器已不存在
docker ps -a --filter name=rabbitmq --format "{{.Status}}" | grep -q . && \
  echo "FAIL: RabbitMQ container still exists -- abort" && exit 1
```
> **阻断理由**: `git rm --cached` 配合磁盘删除在容器运行时有竞态风险——RMQ 可能正在写入 mnesia。必须在容器完全停止后执行。

# 从 git index 移除 38 个运行时文件（保留磁盘副本）
git rm --cached -r rabbitmq/.erlang.cookie rabbitmq/mnesia/
# 提交解除跟踪
git commit -m "Chore: untrack RabbitMQ runtime data from Git"
```
`.gitignore` 中 `rabbitmq/` 规则已在 V2.1 添加，此步确保已跟踪文件也被移除。

**Step 2 — 清理磁盘残留**:
```bash
rm -rf rabbitmq/.erlang.cookie rabbitmq/mnesia/
```

**Step 3 — 创建 RabbitMQ 配置文件**:
`rabbitmq/rabbitmq.conf`:
```ini
## RabbitMQ 4.0 — Phase 3-A 启动配置
cluster_formation.random_node_name = false
nodename = rabbit@localhost
```

**Step 4 — compose.yml 切换为 named volume**:
仅 RabbitMQ 数据目录从 bind-mount 切换为 named volume。其他服务（MySQL, Redis, ES 等）保持 bind-mount——开发环境需要直接访问数据文件。

```yaml
# compose.yml 顶级 volumes 新增声明
volumes:
  rabbitmq-data:

# services.rabbitmq.volumes
services:
  rabbitmq:
    volumes:
      - ./rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro
      - rabbitmq-data:/var/lib/rabbitmq
```

> **设计原则**: RabbitMQ 是唯一需要 named volume 的中间件。cookie 权限问题本质是 macOS bind-mount 的 POSIX 权限缺陷——容器的 `chmod 600` 被宿主机 FS 忽略。named volume 由 Docker daemon 管理，权限在容器侧生效。MySQL/Redis/ES/MinIO/Nacos 无此类敏感权限文件，保留 bind-mount。

**Step 5 — healthcheck 添加 start_period**:
   ```yaml
   healthcheck:
     test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
     interval: 10s
     timeout: 5s
     retries: 5
     start_period: 30s
   ```

**验证方式**:
- `git ls-files rabbitmq/ | grep -E "conf|plugins|definitions"` → 非空（配置文件受 Git 管理）
- `git ls-files rabbitmq/ | grep -E "data|mnesia|cookie"` → 空（运行时数据已排除）（G6 验收）
- `docker compose up -d rabbitmq && sleep 45 && docker compose ps rabbitmq` → `(healthy)`（G1 验收）
- `docker compose down && docker compose up -d rabbitmq` → 第二次启动也 healthy（G3 验收）
- `docker compose down -v && docker compose up -d rabbitmq` → named volume 重建后也 healthy
- `ls rabbitmq/` → 仅有 `rabbitmq.conf`，无 `mnesia/`、无 `.erlang.cookie`

---

### T2 — Redis / ES / MinIO / Nacos 数据生命周期治理

| 属性 | 值 |
|------|-----|
| **风险等级** | P1 — 不影响当前运行，但开发体验差（clone 后 compose up 可能遇残留数据冲突） |
| **修改范围** | `.gitignore` (复核), 残留运行时数据 (磁盘删除), MinIO 镜像版本固定 |
| **依赖** | 无 |
| **预计影响文件** | compose.yml (1 行 MinIO 版本), 磁盘清理 (无 git 变更) |

**方案**:

**T2.1 — 清理磁盘残留**（无 git 变更——这些文件已在 V2.1 B2 通过 `git rm --cached` 解除跟踪）:
```bash
# Elasticsearch
rm -rf elasticsearch/_state elasticsearch/nodes elasticsearch/node.lock elasticsearch/snapshot_cache
# MinIO
rm -rf minio/.minio.sys
# Nacos
rm -rf nacos/derby-data nacos/protocol/raft nacos/naming nacos/connection nacos/tps
# Redis (保留配置，仅清理 data)
rm -rf redis/data/*
# RabbitMQ — 见 T1 Step 2（需同时 git rm --cached）
```

**T2.2 — .gitignore 复核**:
当前 `.gitignore` 已包含 `elasticsearch/`, `minio/`, `nacos/`, `rabbitmq/`, `redis/data/`, `mysql/data/`。复核确认：`mysql/ms-data/` 也在列表中。无遗漏。

**T2.3 — MinIO 镜像版本固定**（compose.yml）:
```yaml
# 从
image: minio/minio:latest
# 改为
image: minio/minio:RELEASE.2025-09-07T16-13-09Z
```
当前 `latest` 解析为此版本。固定后避免未来拉取引入 breaking changes。

**T2.4 — 卷策略决策表**（Phase 3-A 终态）:
| 服务 | 卷类型 | 理由 |
|------|:---:|------|
| MySQL (主 + 3 从) | bind-mount | 开发时需直接查看数据文件；权限无冲突 |
| Redis + Sentinels | bind-mount | 同上 |
| RabbitMQ | **named volume** | cookie 权限需容器侧管理（T1 修复） |
| Nacos | bind-mount | 权限无冲突 |
| MinIO | bind-mount | 权限无冲突 |
| Elasticsearch | bind-mount | 权限无冲突 |

**验证方式**:
- 清理后 `git status` clean（G4 验收）
- `docker compose down && docker compose up -d` → 全部 healthy
- `docker compose config` 确认 MinIO 版本为非 `latest`
- 各中间件数据目录在 compose up 后自动重建

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
| minio | 无 start_period | `start_period: 10s` | MinIO 启动快，10s 充裕 |

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
| **前置条件** | 需先创建 ADR 0013 — 记录 springdoc 选型决策 |
| **预计影响文件** | 4 文件 |

**方案**:

**前置: ADR 0013 — API 文档工具选型**:
新建 `docs/decision-log/0013-springdoc-openapi.md`，记录:
- 候选方案: (A) springdoc-openapi, (B) Knife4j, (C) Spring REST Docs, (D) 手工维护
- 选型理由: springdoc 为 Spring Boot 3.x 官方推荐，零侵入注解，自动生成 OpenAPI 3.0 规范，dev 可见/prod 关闭
- 版本锁定: `2.7.0`（Spring Boot 3.5 兼容）
- Profile 策略: dev 启用 Swagger UI + api-docs，prod 全部关闭

ADR 批准后方可执行以下实施步骤。

**Step 1 — pom.xml 添加依赖**:
1. `pom.xml` 添加:
   ```xml
   <dependency>
       <groupId>org.springdoc</groupId>
       <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
       <version>2.7.0</version>
   </dependency>
   ```

**Step 2 — 新增 OpenApiConfig.java**:
2. 新增 `OpenApiConfig.java` — 定制 API 文档标题/版本信息

**Step 3 — 标记废弃人工文档**:
3. `API-Reference.md` 顶部添加废弃声明:
   ```markdown
   > **⚠️ 此文档已被 Springdoc OpenAPI 替代（Phase 3-A）。**
   > 开发环境: http://localhost:8080/swagger-ui.html
   ```

**Step 4 — Controller 渐进添加注解**:
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

## 5. Phase 3-A 分支策略

> **原则**: V2.1 基线 tag (`v2.1.0`) 不可变。所有 Phase 3-A 工作在新分支上进行。

### 5.1 分支创建

```bash
# 从冻结 tag 创建功能分支
git checkout -b codex/phase-3a-infra-stabilization v2.1.0
```

### 5.2 分支规则

| 规则 | 说明 |
|------|------|
| **基底** | 必须从 `v2.1.0` tag 切出，不基于 `main` HEAD |
| **命名** | `codex/phase-3a-infra-stabilization` |
| **提交粒度** | 每个 T 任务独立 commit，commit message 格式: `Phase-3A/T{n}: {简述}` |
| **合入方式** | `git merge --no-ff` 保留分支拓扑 |
| **合入前检查** | G1-G9 全部通过 |
| **禁止** | force push、rebase、修改 `v2.1.0` tag |

### 5.3 提交序列

```
v2.1.0 (ec9a158)  ← 基线 tag，不可变
  │
  ├── Phase-3A/T1: RabbitMQ git untrack + named volume
  ├── Phase-3A/T2: Clean runtime residues + pin MinIO version
  ├── Phase-3A/T3: Docker Compose healthcheck hardening
  ├── Phase-3A/T4: JWT profile-aware security policy
  ├── Phase-3A/ADR-0013: Springdoc OpenAPI decision record
  ├── Phase-3A/T5: Springdoc OpenAPI integration
  └── Phase-3A/T6: Compose profiles for service tiers
```

---

## 6. 执行顺序与依赖关系

```
Phase 3-A 执行拓扑:

T0 (git rm --cached rabbitmq) ──→ T1 (RabbitMQ named vol) ──┐
                                                               ├──→ T3 (Healthcheck) ──→ T6 (Profiles)
T2 (Data Lifecycle + MinIO pin) ──────────────────────────────┘
                                                               
T4 (JWT Security) ──→ 独立执行

ADR-0013 ──→ T5 (API Docs) ──→ 独立执行
```

| 轮次 | 任务 | 理由 |
|:---:|------|------|
| Round 0 | T1 Step 1 (git rm --cached) | 必须先于任何 compose 操作，解除 38 文件跟踪 |
| Round 1 | T1 Steps 2-5 + T2 并行 | 基础设施层，无代码变更 |
| Round 2 | T3 | 依赖 T1 named volume 修复（RMQ healthcheck 才能通过） |
| Round 3 | T4 | 独立执行，涉及 Java 代码重构 |
| Round 4 | ADR-0013 → T5 | ADR 先审批，T5 后实施 |
| Round 5 | T6 | 依赖 T3 完成后 compose 状态稳定 |

---

## 7. 影响范围总览

| 任务 | compose.yml | git index | Java 代码 | YAML 配置 | 配置文件 | 新增文件 | 删除(残留) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| T1 | ✓ | ✓ (git rm --cached 38 files) | — | — | `rabbitmq.conf` | 1 | mnesia + cookie |
| T2 | ✓ (MinIO pin) | — | — | — | — | 0 | ES/Nacos/MinIO/RabbitMQ 残留 |
| T3 | ✓ | — | — | — | — | 0 | — |
| T4 | — | — | JwtUtils, JwtConfig, AuthService, JwtAuthFilter | application*.yml × 3 | — | 1 (JwtConfig) | — |
| ADR-0013 | — | — | — | — | — | 1 (ADR doc) | — |
| T5 | — | — | OpenApiConfig | — | — | 1 (OpenApiConfig) | — |
| T6 | ✓ | — | — | — | — | 0 | — |

---

## 8. 风险与回滚

| 任务 | 最大风险 | 回滚方式 |
|:---:|------|------|
| T1 | named volume 在 macOS Docker Desktop 下行为异常 | 回退为 bind-mount + 预置 `chmod 600 rabbitmq/.erlang.cookie` |
| T1 | `git rm --cached` 误删配置文件 | `git ls-files rabbitmq/` 确认仅 `.erlang.cookie` + `mnesia/` 被跟踪；`rabbitmq.conf` 为新建文件不受影响 |
| T2 | 误删 redis/data 导致持久化数据丢失 | data/ 仅测试数据；可先 `tar czf redis/data.tar.gz redis/data/` 备份 |
| T3 | Sentinels healthcheck 命令错误导致 healthy 假阳性 | healthcheck 失败反而暴露真实问题，优于无检测 |
| T4 | JwtUtils static→instance 链式影响超出预期 | `git revert`，回退至 static 版本，仅 2 处调用方 |
| ADR-0013 | ADR 未批准即实施 T5 | 严格门禁：ADR merge 到 main 后方可开始 T5 编码 |
| T5 | springdoc 版本与 Spring Boot 3.5 不兼容 | 降级 springdoc 版本或回退至纯文档维护 |
| T6 | profiles 分离后发现 compose 启动顺序依赖跨 profile | profiles 是纯标记，不改变启动顺序 |

---

> **Phase 3-A 目标声明**: 完成上述 T1-T6 后，项目达到"基础设施可重复启动"基线，可安全进入 Phase 3-B（中间件功能集成：Redis 缓存、RabbitMQ 消息、Elasticsearch 搜索）。
| G6 | RabbitMQ Git 生命周期规则生效：配置文件受跟踪，运行时数据排除 | `git ls-files rabbitmq/ | grep -E "conf|plugins|definitions"` → 非空；`git ls-files rabbitmq/ | grep -E "data|mnesia|cookie"` → 空 |
| G6 | `git ls-files` 确认 RabbitMQ 38 文件已解除跟踪（`git rm --cached` 生效） | `git ls-files rabbitmq/ | wc -l` → `0` |
| G7 | V2.1 基线 tag 不被修改，Phase 3-A 所有工作在独立分支上 | `git tag -l 'v2.1.0'` 仍指向 `ec9a158` |
| G9 | 外部依赖镜像标签均为固定版本（无 `:latest`），排除本地 build 镜像 | 扫描范围: `compose.yml` 外部镜像 + `Dockerfile` FROM 指令 + `deploy/` 脚本；`grep -E 'image:.*:latest' compose.yml | grep -v 'industrial-ai-hub'` → 0 匹配 |
| G9 | `compose.yml` 中所有镜像标签均为固定版本（无 `:latest`） | `rg ':latest' compose.yml` → 0 匹配 |
