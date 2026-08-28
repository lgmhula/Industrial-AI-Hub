# Industrial AI Hub — 全方位项目审查报告

> **项目版本**：v2.2.0 | **审查日期**：2026-08-21 | **审查范围**：前后端 + 基础设施 + CI/CD
>
> **综合完成度**：55% — 核心设备管理闭环已打通，但企业级运营能力（权限管理、多租户、备份、通知）大面积缺失。

---

## 评分总览

| 维度 | 评分 | 关键发现 |
|------|:----:|----------|
| 安全审查 | 7.0/10 | SQL注入 ✅ / 水平越权 🔴 |
| 性能工程化 | 5.2/10 | 懒加载 ✅ / 构建优化 ⚠️ |
| 功能完整性 | 5.5/10 | 设备闭环 ✅ / 权限体系 🔴 |
| 高危问题 | 9 项 | 需立即修复 |
| 中危问题 | 16 项 | 尽快修复 |
| 低危问题 | 12 项 | 计划修复 |

---

## 目录

1. [项目结构与技术栈](#1-项目结构与技术栈)
2. [安全审查](#2-安全审查)
3. [性能与工程化审查](#3-性能与工程化审查)
4. [企业产品功能完整性评估](#4-企业产品功能完整性评估)
5. [风险优先级矩阵](#5-风险优先级矩阵)
6. [改进路线图](#6-改进路线图)

---

## 1. 项目结构与技术栈

### 1.1 整体架构

**前后端分离架构**，开发期 Vite Dev Server 代理 `/api` 到 Spring Boot，部署期 Nginx 反代后端 + 静态资源。

```
开发期：  浏览器 → Vite (5173) → proxy /api → Spring Boot (8080)
部署期：  浏览器 → Nginx → Spring Boot (8080) / 静态资源
```

**后端分层架构**（`dev.reboot` 包下）：

```
controller/   → REST 控制器（6 个：Auth/Device/DeviceData/Alarm/OperationLog/User）
service/      → 业务逻辑（7 个 Service）
mapper/       → MyBatis Mapper 接口（7 个）+ XML 映射文件
entity/       → 领域实体（7 个：User/Role/UserRole/Device/DeviceData/Alarm/OperationLog）
dto/          → 数据传输对象（ApiResponse 统一响应 + VO/DTO/Request）
config/       → 配置类（8 个：Cache/Cors/Jwt/Knife4j/MQ/Redis/Security/WebMvc）
security/     → JWT 认证过滤器 + RBAC 拦截器 + 限流拦截器
aop/          → 操作日志切面
mq/           → RabbitMQ 生产者/消费者（设备数据同步 + 报警通知 + 报警升级）
rule/         → 报警规则引擎（规则配置 + 操作符 + 规则匹配）
enums/        → 错误码 + 角色枚举
exception/    → 业务异常 + 全局异常处理
```

**前端模块划分**：

```
views/        → 6 个页面（Login/Dashboard/DeviceList/DeviceDetail/AlarmList/OperationLogList）
api/          → 统一 Axios 实例 + API 分组（auth/device/deviceData/alarm/operationLog）
router/       → Hash 路由 + 路由守卫（登录态校验）
components/   → 通用组件（EmptyState/LoadingSpinner）
```

### 1.2 前端技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Vue 3 | ^3.5.39 | Composition API |
| 路由 | Vue Router | ^4.6.4 | Hash 模式（`createWebHashHistory`） |
| UI 库 | Element Plus | ^2.14.3 | 按需引入（unplugin 自动导入） |
| 图标 | @element-plus/icons-vue | ^2.3.2 | — |
| HTTP | Axios | ^1.18.1 | 封装拦截器（JWT 注入 + 业务码校验） |
| 图表 | ECharts + vue-echarts | ^6.1.0 / ^8.0.1 | Dashboard 数据可视化 |
| 构建工具 | Vite | ^8.1.1 | 固定端口 5173 |
| 自动导入 | unplugin-auto-import | ^21.0.0 | Element Plus API 按需导入 |
| 组件自动注册 | unplugin-vue-components | ^32.1.0 | Element Plus 组件按需注册 |
| 状态管理 | ❌ 无 | — | 直接使用 `localStorage` 存取 token，无 Pinia/Vuex |
| 测试框架 | ❌ 无 | — | 前端零测试覆盖 |

### 1.3 后端技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java (JDK 25 LTS, Temurin) | 25 | 唯一运行时 |
| 框架 | Spring Boot | 3.5.0 | parent POM 锁定 |
| 构建 | Maven | 3.9.6 | Wrapper 锁定 |
| ORM | MyBatis Spring Boot | 3.0.5 | XML Mapper + 注解 |
| 分页 | PageHelper | 2.1.0 | — |
| 数据库 | MySQL | 8.4 | Docker 容器 |
| 数据库迁移 | Flyway | BOM 管理 | ADR 0019，V1-V3 迁移脚本 |
| 缓存 | Spring Data Redis | BOM 管理 | Lettuce 连接池 |
| Redis 客户端 | Jedis | 5.2.0 | Day 43 学习数据类型 |
| 分布式锁 | Redisson | 3.39.0 | Day 46 |
| 消息队列 | RabbitMQ | 4.0-management | Spring AMQP |
| 认证 | JJWT | 0.12.6 | JWT 生成/验证 |
| 密码加密 | spring-security-crypto | BOM 管理 | BCrypt（非完整 Spring Security） |
| API 文档 | Knife4j | 4.5.0 | OpenAPI 3 |
| 限流 | Guava RateLimiter | 33.4.0-jre | — |
| AOP | spring-boot-starter-aop | BOM | 操作日志切面 |
| 监控 | spring-boot-starter-actuator | BOM | 仅暴露 /actuator/health |
| 测试 DB | H2 | BOM (test scope) | 测试隔离 |

> ⚠️ **Redis 客户端三重叠加**：同时引入 Jedis + Spring Data Redis(Lettuce) + Redisson，三套客户端共存，连接池管理复杂度上升。

### 1.4 前后端通信方式

**纯 REST API（HTTP + JSON）**，无 GraphQL、无 WebSocket。

| 环节 | 细节 |
|------|------|
| 协议 | HTTP REST，所有接口前缀 `/api/` |
| 数据格式 | JSON（`Content-Type: application/json`） |
| 统一响应 | `ApiResponse<T>` → `{ code, message, data }` |
| 认证方式 | JWT Bearer Token（请求头 `Authorization: Bearer <token>`） |
| Token 传递 | 前端 `localStorage` 存取 → Axios 请求拦截器注入 |
| 业务码校验 | HTTP 200 不代表成功，前端额外校验 `body.code === 200` |
| 跨域 | 开发期 Vite proxy；部署期 Nginx 反代；后端 CORS 白名单 |
| 实时通信 | ❌ 无 WebSocket / SSE（报警通知走 RabbitMQ → 消费端落库 → 前端轮询） |

### 1.5 依赖包分析

**功能重复检查**：

| 领域 | 情况 | 结论 |
|------|------|------|
| Redis 客户端 | Jedis + Lettuce(Spring Data Redis) + Redisson | ⚠️ 功能重叠，建议生产环境统一为 Redisson |
| JSON 处理 | Jackson（Spring Boot 默认） | ✅ 无重复 |
| 日志框架 | Logback（Spring Boot 默认） | ✅ 无重复 |
| HTTP 客户端 | 仅 Axios（前端） | ✅ 无重复 |
| 校验 | Bean Validation（后端） | ✅ 无重复 |

**过时/漏洞总结**：

- 无已知高危漏洞依赖：JJWT 0.12.6、Guava 33.4.0、Spring Boot 3.5.0 均为当前最新稳定版
- 无过时依赖：所有依赖版本均为 2024-2025 年发布的新版本
- 主要风险点：Redis 三客户端共存 + 自定义安全链（无 Spring Security 框架保护）+ 前端零测试覆盖

---

## 2. 安全审查

### 2.1 敏感信息 — ✅ 良好

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 配置文件硬编码密钥 | ✅ 安全 | `application.yml` 全部使用 `${VAR}` 占位符 |
| `.env` 密钥强度 | ✅ 安全 | JWT_SECRET / MYSQL_PASSWORD 均为 64 字符随机串 |
| `.env` 是否入 Git | ✅ 安全 | 已列入 `.gitignore` |
| `.env.example` | ✅ 安全 | 仅含占位符，无真实密钥 |
| 全局异常处理 | ✅ 安全 | 返回通用消息 `"服务器内部错误"`，不泄露堆栈 |
| API 文档生产环境 | ✅ 安全 | `application-prod.yml` 中 `springdoc.enabled: false` |
| Docker 安全 | ✅ 安全 | Multi-stage 构建 + `USER app` non-root 运行 |
| JDBC useSSL | ⚠️ 中危 | 默认 `useSSL=false`，生产环境需覆盖为 `true` |
| Dev fallback 密钥 | ⚠️ 中危 | `JwtConfig` dev 环境固定 fallback 值 `dev-fallback-key-at-least-256-bits...` |

### 2.2 认证鉴权 — ⚠️ 多项缺失

**JWT Token 管理**：

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 签名算法 | ✅ | HS256（HMAC-SHA256），通过 `Keys.hmacShaKeyFor` 构造 |
| 密钥强度 | ✅ | 256-bit（64 字符），通过 `.env` 注入 |
| 签名验证 | ✅ | `Jwts.parser().verifyWith(key).parseSignedClaims(token)` |
| 过期检查 | ✅ | 隐式包含在 `parseSignedClaims` 中 |
| Claims 解析 | ✅ | 正确提取 userId/username/roles |
| 密码存储 | ✅ | BCrypt 加密 |
| 登录错误信息 | ✅ | 用户不存在和密码错误均返回 `"用户名或密码错误"` |
| 登录接口限流 | 🔴 高危 | `/api/auth/**` 被排除在限流外，无暴力破解防护 |
| 账户锁定机制 | 🔴 高危 | 连续失败登录不会触发锁定 |
| Token 刷新机制 | 🔴 高危 | 无 refresh token，24h 过期后需重新登录 |
| Token 黑名单/吊销 | ⚠️ 中危 | 用户禁用后已有 Token 仍有效 |
| 密码复杂度 | ⚠️ 中危 | 仅 `@Size(min=6)`，无大小写/数字要求 |

### 2.3 接口安全 — 🔴 水平越权

所有业务接口均带 `@RequireRole` 注解 ✅，但存在严重的水平越权问题：

| 接口 | 问题 | 风险 |
|------|------|------|
| `GET /api/devices/{id}` | 无设备所有权校验，任何 VIEWER 可查看任意设备 | 🔴 高危 |
| `PUT /api/devices/{id}` | 任何 OPERATOR 可修改任意设备 | 🔴 高危 |
| `POST /api/device-data/device/{deviceId}` | 任何 OPERATOR 可向任意设备上报虚假数据 | 🔴 高危 |
| `PUT /api/alarms/{id}/acknowledge` | 任何 OPERATOR 可确认/解决任意报警 | 🔴 高危 |
| 限流粒度 | 仅 URI 级别，无 per-user/per-IP 限流 | ⚠️ 中危 |

> **修复建议**：在 Service 层增加 `userId` 与 `device.ownerId` 的归属校验，或引入"设备-用户"关联表。

### 2.4 XSS / CSRF

| 检查项 | 状态 | 说明 |
|--------|------|------|
| `v-html` 使用 | ✅ 安全 | 6 个页面均未使用 |
| 动态数据渲染 | ✅ 安全 | 全部使用 `{{ }}` 文本插值，Vue 自动转义 |
| CSRF Token | 🔴 高危 | 前端未发送，后端未校验 |
| AllowCredentials | ⚠️ 中危 | `true` + 无 CSRF 叠加风险 |

> **CSRF 风险评估**：当前使用 Bearer Token（Authorization 头），不依赖 Cookie，传统 CSRF 攻击面较小。但 `AllowCredentials: true` 开启了凭证模式，若未来引入 Cookie 认证则风险立即升高。

### 2.5 SQL 注入 — ✅ 安全

> **全部通过**：7 个 Mapper 接口 + 2 个 XML 映射文件全部使用 `#{}` 参数化查询，无 `${}` 拼接，无动态 ORDER BY / LIKE 注入。

| 检查项 | 状态 |
|--------|------|
| `${}` 拼接 | ✅ 无 |
| 动态 ORDER BY | ✅ 全部静态硬编码 |
| LIKE 注入 | ✅ 使用 `CONCAT('%', #{keyword}, '%')` |
| 动态 SQL `<if>` | ✅ 参数均为 `#{}` |

### 2.6 依赖安全

**npm audit 结果**（扫描 152 个依赖）：

| 漏洞 | 严重度 | 包 | 问题 | 修复 |
|------|--------|-----|------|------|
| 1 | 🔴 高危 | `nanoid <3.3.18` | 自定义生成器在 size=0 时无限循环（CWE-835, CVSS 5.9） | `npm audit fix` |

后端 Maven 依赖：Spring Boot 3.5.0 / JJWT 0.12.6 / Guava 33.4.0 均为最新稳定版，无已知 CVE。

### 2.7 CORS 配置 — ⚠️ 需收紧

```java
// CorsConfig.java
config.setAllowedOriginPatterns(List.of(origins.split(",")));
config.addAllowedMethod("*");       // ⚠️ 通配方法
config.addAllowedHeader("*");       // ⚠️ 通配头
config.setAllowCredentials(true);   // ⚠️ 凭证模式
```

| 问题 | 风险 | 修复建议 |
|------|------|----------|
| AllowCredentials + 无 CSRF | ⚠️ 中危 | 关闭 AllowCredentials 或加 CSRF Token |
| `allowedMethod("*")` | ⚠️ 中危 | 限制为 GET/POST/PUT/DELETE/OPTIONS |
| `allowedHeader("*")` | ⚠️ 低危 | 限制为 Authorization/Content-Type/Accept |
| 生产环境白名单 | ✅ 已支持 | 通过 CORS_ORIGINS 环境变量注入 |

---

## 3. 性能与工程化审查

### 3.1 打包体积 — ⚠️ 多项缺失

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 路由级分割 | ✅ | 5 个页面懒加载（`() => import()`） |
| Vendor 分割 | ❌ 缺失 | 未配置 `manualChunks`，全部打进单 chunk |
| Element Plus 按需 | ✅ | `unplugin-auto-import` + `unplugin-vue-components` |
| **图标全量引入** | 🔴 高危 | `main.js` 全量注册 280+ 图标，增加 ~50-80KB gzip |
| 构建时压缩 | ❌ 缺失 | 无 `vite-plugin-compression` |
| Brotli 压缩 | ❌ 缺失 | 未配置 |

> **图标全量引入问题**：`main.js` 中 `import * as ElementPlusIconsVue` 注册了全部 280+ 个图标，实际仅使用 ~10 个（Monitor/Odometer/Cpu/Bell/Document 等）。

### 3.2 首屏加载 — ✅ 基本良好

| 依赖 | 首屏加载 | 大小估算 | 说明 |
|------|---------|----------|------|
| Vue 3 | ✅ | ~35KB gzip | 必需 |
| Vue Router | ✅ | ~15KB gzip | 必需 |
| Element Plus | ✅ 按需 | ~30-50KB gzip | 必需 |
| **图标全量** | ✅ | ~50-80KB gzip | ❌ 仅需 10 个 |
| Axios | ✅ | ~13KB gzip | 必需 |
| ECharts | ❌ 懒加载 | ~300KB gzip | Dashboard 页才加载 ✅ |
| 资源预加载 | ❌ | — | 无 preload/prefetch/preconnect |

### 3.3 图片资源 — ✅ 良好

仅 1 个 `favicon.svg`（12KB 矢量格式），无需压缩/WebP/CDN。

### 3.4 缓存策略 — ✅ 基本良好

```nginx
# nginx.conf — 静态资源长缓存
location ~* \.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?)$ {
    expires 30d;
    add_header Cache-Control "public, immutable";  # ✅ hash 文件名
}
```

> ⚠️ **缺失**：`index.html` 未设置 `Cache-Control: no-cache`，用户可能拿到旧入口文件。

### 3.5 构建优化 — ⚠️ 配置过于精简

| 缺失项 | 影响 | 优先级 |
|--------|------|--------|
| 无 `manualChunks` 配置 | vendor 全打一个 chunk | P1 |
| 无 `vite-plugin-compression` | 无构建时 .gz/.br 预压缩 | P1 |
| 无 `rollup-plugin-visualizer` | 无法分析 bundle 构成 | P2 |
| 无 `build.target` 显式声明 | 默认 modules | P3 |
| 无 drop console 配置 | 生产环境泄露 console.log | P2 |

### 3.6 环境管理

| 环境 | 后端 | 前端 |
|------|------|------|
| dev | ✅ application-dev.yml + .env | ❌ 无 .env |
| test | ✅ application-test.yml (H2) | ❌ 无 |
| prod | ✅ application-prod.yml | ❌ 无 .env.production |
| staging | ❌ 无 | ❌ 无 |

> 🔴 **前端无任何环境变量管理**：`baseURL: '/api'` 硬编码，若前端独立部署到 CDN 则无法工作。

### 3.7 CI/CD

**CI — ✅ 基本合格**：

| 检查项 | 状态 |
|--------|------|
| GitHub Actions | ✅ |
| 后端构建+测试（89 测试，H2 隔离） | ✅ |
| 前端构建 | ✅ |
| Node 22 + JDK 25 锁定 | ✅ |
| Maven/npm 缓存 | ✅ |
| Qodana 代码质量扫描 | ✅ |
| 分支保护（ADR 0017） | ✅ |

**CD — ❌ 缺失**：

| 缺失项 | 影响 | 优先级 |
|--------|------|--------|
| 无 CD 流水线 | 部署完全手动（deploy.sh SSH+scp） | P1 |
| 无 Docker 镜像构建 CI | CI 不构建镜像 | P2 |
| 无前端部署自动化 | dist/ 需手动上传 | P1 |
| 无灰度/蓝绿部署 | 直接重启，停机不可控 | P2 |
| 无回滚机制 | deploy.sh 不保留旧版本 | P2 |
| 无 E2E 测试 | CI 仅构建不验证功能 | P3 |

### 3.8 日志监控 — 🔴 前端完全缺失

| 检查项 | 后端 | 前端 |
|--------|------|------|
| 日志框架 | ✅ Logback | ❌ 无 |
| 日志级别配置 | ✅ INFO | ❌ 无 |
| 结构化 JSON 日志 | ❌ 默认格式 | ❌ 无 |
| 日志轮转 | ❌ 无 logback-spring.xml | ❌ 无 |
| 错误监控（Sentry） | ❌ 无 | ❌ 无 |
| 链路追踪 | ❌ 无 Sleuth | ❌ 无 Web Vitals |
| 操作日志 | ✅ AOP 落库 | N/A |

> 🔴 **前端监控完全空白**：生产环境前端错误对开发团队完全不可见，用户遇到白屏、接口报错等问题无法主动发现。

---

## 4. 企业产品功能完整性评估

### 4.1 核心业务模块（6 个）

| 模块 | 前端页面 | 后端 API | 数据库表 | 闭环状态 |
|------|---------|----------|----------|----------|
| 认证管理 | Login.vue | 2 API | user, role, user_role | ✅ 登录闭环 |
| 设备管理 | DeviceList + DeviceDetail | 5 API | device | ✅ CRUD 完整 |
| 设备数据 | DeviceDetail 内嵌 | 5 API | device_data | ✅ 上报+查询 |
| 报警管理 | AlarmList.vue | 5 API | alarm | ⚠️ 通知未接入 |
| 操作日志 | OperationLogList.vue | 3 API | operation_log | ✅ 记录+查询 |
| 仪表盘 | Dashboard.vue | 复用 | — | ⚠️ 仅只读 |

**核心业务链路**：

```
设备注册 → 数据上报 → 报警检测 → 报警通知(MQ) → 报警处理
    ✅         ✅         ✅        ⚠️(仅日志)    ✅(确认/解决)
```

### 4.2 功能缺失分析

**CRUD 完整性矩阵**：

| 模块 | 增 | 删 | 改 | 查 | 缺失项 |
|------|:--:|:--:|:--:|:--:|--------|
| 设备 | ✅ | ✅ | ✅ | ✅ | 批量操作、导入导出 |
| 设备数据 | ✅(上报) | ❌ | ❌ | ✅ | 无删除/修改（业务合理） |
| 报警 | 自动 | ❌ | ✅(状态) | ✅ | 无批量确认、无删除 |
| 用户 | ❌ | ✅ | ⚠️部分 | ✅ | **无管理员创建用户** |
| 角色 | ❌ | ❌ | ❌ | ❌ | **完全缺失** |
| 报警规则 | ❌ | ❌ | ❌ | ❌ | **完全缺失（硬编码）** |
| 操作日志 | 自动 | ❌ | ❌ | ✅ | 无导出、无高级筛选 |

**关键功能缺失清单**：

| 优先级 | 缺失功能 | 影响 |
|--------|----------|------|
| 🔴 P0 | 角色管理 API — 无增删改查角色接口 | 权限体系不完整 |
| 🔴 P0 | 用户角色分配 API — 无法给用户分配/取消角色 | 管理员无法管理用户权限 |
| 🔴 P0 | 管理员创建用户接口 — 注册在 AuthController，非后台功能 | 用户管理不完整 |
| 🔴 P0 | 密码重置接口 — changePassword 依赖旧密码 | 用户忘记密码无应急 |
| 🟡 P1 | 报警规则管理 API — 规则硬编码在 AlarmRuleConfig | 无法动态调整规则 |
| 🟡 P1 | 报警通知接入 — 死信队列和升级仅日志输出 | 报警无法触达用户 |
| 🟡 P1 | 批量操作 — 设备批量删除、报警批量确认 | 大数据量效率低 |
| 🟡 P1 | 导出功能 — 设备/报警/日志均无导出 | 审计数据无法离线分析 |
| 🟡 P1 | 注册页面 — authApi.register 已定义但无前端入口 | 用户无法自助注册 |
| 🟢 P2 | 404 页面 — 路由无通配符处理 | 用户输入错误 URL 白屏 |
| 🟢 P2 | 操作日志高级筛选 — 无时间范围/操作类型筛选 | 日志检索能力不足 |
| 🟢 P2 | 设备分组/模板 — 无设备分组概念 | 设备量大时管理困难 |

### 4.3 用户体验

| 页面 | 表单校验 | 操作反馈 | 加载状态 | 分页 | 空状态 | 搜索筛选 |
|------|:--------:|:--------:|:--------:|:----:|:------:|:--------:|
| Login | ⚠️ 基础（手动 if） | ✅ error 显示 | ✅ 按钮 loading | — | — | — |
| Dashboard | N/A | ❌ 无反馈 | ❌ **无 loading** | — | 内联 | ❌ |
| DeviceList | ✅ rules 校验 | ✅ ElMessage | ✅ v-loading | ✅ | ✅ | ✅ |
| DeviceDetail | N/A | ✅ ElMessage | ✅ v-loading | — | ✅ | — |
| AlarmList | N/A | ✅ ElMessage | ✅ v-loading | ✅ | ✅ | ⚠️ 仅状态 |
| OperationLogList | N/A | ✅ ElMessage | ✅ v-loading | ✅ | ✅ | ❌ |

**体验缺失项**：

| # | 缺失项 | 影响 | 优先级 |
|---|--------|------|--------|
| 1 | Dashboard 无 loading 状态 | 数据请求时空白页面 | P1 |
| 2 | 无实时数据刷新（Dashboard/设备详情） | 数据不自动更新 | P2 |
| 3 | 无 WebSocket 实时报警推送 | 报警需手动刷新页面 | P2 |
| 4 | Login 无验证码 | 存在暴力破解风险 | P2 |
| 5 | LoadingSpinner 组件已创建但未使用 | 代码冗余 | P3 |

### 4.4 数据展示

| 页面 | 关键词搜索 | 下拉筛选 | 日期范围 | 重置 | 导出 |
|------|:----------:|:--------:|:--------:|:----:|:----:|
| DeviceList | ✅ 设备名/编码 | ✅ 状态 | ❌ | ✅ | ❌ |
| AlarmList | ❌ | ✅ 状态 | ❌ | N/A | ❌ |
| OperationLogList | ❌ | ❌ | ❌ | N/A | ❌ |

### 4.5 权限体系 — ⚠️ 仅接口级

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 角色定义 | ✅ | ADMIN > OPERATOR > VIEWER（3 个固定角色） |
| 接口级权限 | ✅ | `@RequireRole` 注解覆盖所有接口 |
| 角色管理 API | ❌ 完全缺失 | 无 CRUD 接口 |
| 用户角色分配 | ❌ 完全缺失 | 注册默认 VIEWER，无法变更 |
| 菜单权限 | ❌ 缺失 | 前端菜单全部可见 |
| 按钮级权限 | ❌ 缺失 | 无 v-permission 指令 |
| 数据权限 | ❌ 缺失 | 无行级隔离 |
| 路由级守卫 | ❌ 缺失 | 仅登录检查，无角色检查 |

### 4.6 国际化 / 多租户 — ❌ 零预留

| 检查项 | 国际化 | 多租户 |
|--------|--------|--------|
| 框架/库 | ❌ 无 vue-i18n | ❌ 无 tenant_id |
| 文本提取 | ❌ 硬编码中文 | ❌ 无隔离查询 |
| 数据库预留 | ❌ 无 locale 字段 | ❌ 无 tenant 表 |
| Element Plus 语言包 | ✅ zhCn 已引入 | N/A |

### 4.7 数据备份与恢复 — ❌ 完全缺失

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 备份记录表 | ❌ | 无 backup_record 表 |
| 定时备份任务 | ❌ | 无 Spring Scheduled |
| 数据归档机制 | ❌ | device_data 无限增长 |
| 日志清理机制 | ❌ | operation_log 无清理 |
| 恢复接口 | ❌ | 无恢复 API |
| MySQL binlog | ✅ | 默认开启 |

> 🔴 **device_data 表无保留策略**：设备数据持续上报但无归档/清理机制，表会无限膨胀，查询性能将持续下降。

### 4.8 模块级完成度评分

| 模块 | 完成度 | 评分 | 关键缺口 |
|------|:-------:|:----:|----------|
| 设备管理 | 85% | ★★★★☆ | 缺批量操作、导入导出 |
| 设备数据 | 80% | ★★★★☆ | 缺数据保留策略 |
| 操作日志 | 70% | ★★★★☆ | 缺导出、高级筛选 |
| 报警管理 | 60% | ★★★☆☆ | 规则硬编码、通知未接入 |
| 仪表盘 | 60% | ★★★☆☆ | 缺 loading、实时刷新 |
| 用户管理 | 40% | ★★☆☆☆ | 缺创建/角色分配/密码重置 |
| 权限体系 | 30% | ★★☆☆☆ | 仅接口级，无菜单/按钮权限 |
| 角色管理 | 0% | ☆☆☆☆☆ | 完全缺失 |
| 国际化 | 0% | ☆☆☆☆☆ | 无预留 |
| 多租户 | 0% | ☆☆☆☆☆ | 无预留 |
| 数据备份 | 0% | ☆☆☆☆☆ | 无机制 |

```
核心业务闭环：  ████████░░  80%  (设备→数据→报警→处理 链路已通)
企业级能力：    ███░░░░░░░  30%  (权限/多租户/备份 大面积缺失)
用户体验：      ███████░░░  70%  (核心页面体验良好，细节待打磨)
工程化程度：    ██████░░░░  60%  (CI 有但 CD 缺失，监控空白)
──────────────────────────────────────────────────
综合完成度：    ██████░░░░  55%
```

---

## 5. 风险优先级矩阵

### 🔴 P0 — 立即修复（阻塞上线）

| # | 问题 | 维度 | 修复方案 |
|---|------|------|----------|
| 1 | 水平越权 — Device/DeviceData/Alarm 无所有权校验 | 接口安全 | Service 层增加 userId 归属校验 |
| 2 | 登录接口无限流 + 无账户锁定 | 认证鉴权 | 添加 IP 级限流 + 失败锁定机制 |
| 3 | 无 Token 刷新/黑名单机制 | 认证鉴权 | 实现 refresh token + Redis 黑名单 |
| 4 | nanoid 高危漏洞 | 依赖安全 | `npm audit fix` |
| 5 | 图标全量引入 — main.js 注册 280+ 图标 | 打包体积 | 替换为按需 import |
| 6 | 角色管理 API 缺失 | 功能完整性 | 新增角色 CRUD + 权限分配 |
| 7 | 用户角色分配缺失 | 功能完整性 | 新增用户-角色关联 API |
| 8 | 数据保留策略缺失 — device_data 无限增长 | 数据备份 | 定时归档/清理机制 |
| 9 | 前端环境变量缺失 | 环境管理 | 创建 .env.development / .env.production |

### 🟡 P1 — 尽快修复（影响生产质量）

| # | 问题 | 维度 | 修复方案 |
|---|------|------|----------|
| 1 | 无 Token 黑名单/吊销机制 | 认证鉴权 | Redis 维护 Token 黑名单 |
| 2 | CSRF Token 缺失 + AllowCredentials | XSS/CSRF | 关闭 AllowCredentials 或加 CSRF |
| 3 | JDBC useSSL=false 生产环境 | 敏感信息 | 生产环境覆盖为 useSSL=true |
| 4 | DTO 缺少 @Size/@Pattern/@Min/@Max | 接口安全 | 补全所有 DTO 校验注解 |
| 5 | 限流仅 URI 级，无 per-user/per-IP | 接口安全 | 改为基于用户/IP 的限流 |
| 6 | CORS 通配方法/头 | CORS | 限制具体方法和头 |
| 7 | 密码复杂度无要求 | 认证鉴权 | 添加 @Pattern 规则 |
| 8 | Vendor 分包缺失（manualChunks） | 构建优化 | 配置 manualChunks |
| 9 | 构建时压缩缺失 | 构建优化 | 安装 vite-plugin-compression |
| 10 | index.html 无 no-cache | 缓存策略 | Nginx 添加 no-cache |
| 11 | 前端错误监控缺失（Sentry） | 日志监控 | 接入 Sentry |
| 12 | CD 流水线缺失 | CI/CD | GitHub Actions 自动部署 |
| 13 | 后端结构化日志缺失 | 日志监控 | 添加 logback-spring.xml |
| 14 | 报警规则管理 API 缺失 | 功能完整性 | 新增 alarm_rule 表 + CRUD |
| 15 | 报警通知未接入（钉钉/邮件） | 功能完整性 | 接入通知渠道 |
| 16 | 无定时数据库备份 | 数据备份 | mysqldump cron + 备份记录 |

### 🔵 P2 — 计划修复（提升可维护性）

| # | 问题 | 修复方案 |
|---|------|----------|
| 1 | Bundle 分析工具 | 安装 rollup-plugin-visualizer |
| 2 | Drop console 生产构建 | Vite build 配置 |
| 3 | Docker 镜像 CI 构建 | CI 中构建并推送镜像 |
| 4 | 部署回滚机制 | deploy.sh 保留旧版本 |
| 5 | 健康检查门禁 | 部署后验证 /actuator/health |
| 6 | 后端链路追踪 | Micrometer Tracing + Sleuth |
| 7 | 菜单权限控制 | 前端按角色渲染菜单 |
| 8 | 按钮级权限指令 | v-permission 自定义指令 |
| 9 | 路由级权限守卫 | beforeEach 检查角色 |
| 10 | 批量操作（设备/报警） | 批量删除/批量确认 |
| 11 | 导出功能（CSV/Excel） | 设备/报警/日志导出 |
| 12 | Dashboard loading + 自动刷新 | v-loading + setInterval |
| 13 | 操作日志高级筛选 | 时间范围/操作类型 |
| 14 | 注册页面 | 暴露已有 register API |
| 15 | 404 页面 | 路由通配符处理 |
| 16 | 管理员密码重置接口 | UserController 新增 resetPassword |

### 🟢 P3 — 长期规划（SaaS 化准备）

| # | 问题 | 工作量 |
|---|------|--------|
| 1 | 多租户隔离 — tenant_id 注入所有表 + 查询过滤 | 40h |
| 2 | 国际化 i18n — vue-i18n + 后端消息国际化 | 32h |
| 3 | 设备分组/模板 — device_group + device_template | 16h |
| 4 | WebSocket 实时推送 | 16h |
| 5 | 蓝绿/灰度部署 | 16h |
| 6 | ELK 日志采集 | 16h |
| 7 | PWA / Service Worker | 8h |
| 8 | E2E 测试（Playwright） | 8h |
| 9 | Staging 环境 | 8h |
| 10 | 管理员创建用户接口 | 4h |
| 11 | LoadingSpinner 组件集成 | 1h |
| 12 | 资源预加载（preload/prefetch） | 2h |

---

## 6. 改进路线图

### 阶段一：安全加固（预计 3-5 天）

| # | 任务 | 工作量 | 预期收益 |
|---|------|--------|----------|
| 1 | 水平越权修复 — Service 层增加 userId 归属校验 | 8h | 消除最大安全风险 |
| 2 | 登录限流 + 账户锁定 — IP 限流 5 次/分 + 失败 5 次锁定 | 4h | 防暴力破解 |
| 3 | Token 刷新机制 — access 30min + refresh 7d + Redis 黑名单 | 8h | Token 安全闭环 |
| 4 | nanoid 漏洞修复 — npm audit fix | 0.5h | 消除已知 CVE |
| 5 | DTO 校验补全 — @Size/@Pattern/@Min/@Max | 4h | 输入安全 |
| 6 | CORS 收紧 — 限制方法/头 | 1h | 减小攻击面 |

### 阶段二：性能优化（预计 2-3 天）

| # | 任务 | 工作量 | 预期收益 |
|---|------|--------|----------|
| 1 | 图标按需引入 — 替换全量注册为按需 import | 0.5h | 首屏 -50-80KB gzip |
| 2 | Vite 构建时压缩 — vite-plugin-compression | 0.5h | 传输体积 -70% |
| 3 | Vendor 分包 — manualChunks | 0.5h | 并行下载 + 独立缓存 |
| 4 | 前端环境变量 — .env.development / .env.production | 1h | 支持 CDN 部署 |
| 5 | index.html no-cache — Nginx 配置 | 0.5h | 避免旧版本入口 |
| 6 | 前端错误监控 — Sentry 接入 | 2h | 生产错误可见 |

### 阶段三：功能补全（预计 5-7 天）

| # | 任务 | 工作量 | 预期收益 |
|---|------|--------|----------|
| 1 | 角色管理 API — CRUD + 权限分配 | 8h | 权限体系闭环 |
| 2 | 用户管理补全 — 创建/角色分配/密码重置 | 6h | 用户管理闭环 |
| 3 | 报警规则持久化 — alarm_rule 表 + CRUD API | 8h | 规则可动态调整 |
| 4 | 菜单权限 + 按钮级权限 + 路由守卫 | 6h | 前端权限闭环 |
| 5 | 批量操作 + 导出功能 | 8h | 大数据量效率 |
| 6 | 数据保留策略 — device_data 定时归档 | 4h | 防止表膨胀 |
| 7 | 定时数据库备份 — mysqldump cron | 4h | 灾难恢复基础 |

### 阶段四：SaaS 化（预计 4-6 周）

| # | 任务 | 工作量 |
|---|------|--------|
| 1 | 多租户隔离 — tenant_id 注入所有表 + 查询过滤 | 40h |
| 2 | 国际化 i18n — vue-i18n + 后端消息国际化 | 32h |
| 3 | 设备分组/模板 — device_group + device_template | 16h |
| 4 | WebSocket 实时推送 | 16h |
| 5 | ELK 日志采集 | 16h |
| 6 | 蓝绿/灰度部署 | 16h |

---

## 正面发现（已具备的良好实践）

| # | 实践 | 评价 |
|---|------|------|
| 1 | SQL 全部参数化，无注入风险 | ✅ |
| 2 | 前端无 v-html，XSS 防护完善 | ✅ |
| 3 | 密钥无硬编码，.env SSOT 管理 | ✅ |
| 4 | BCrypt 密码加密 | ✅ |
| 5 | JWT 签名验证 + 过期检查完整 | ✅ |
| 6 | 全局异常不泄露堆栈 | ✅ |
| 7 | API 文档生产环境关闭 | ✅ |
| 8 | Docker non-root 运行 | ✅ |
| 9 | Actuator 仅暴露 health | ✅ |
| 10 | RBAC 权限注解全覆盖（接口级） | ✅ |
| 11 | 路由懒加载 5/6 页面 | ✅ |
| 12 | ECharts 懒加载 | ✅ |
| 13 | Element Plus 按需引入 | ✅ |
| 14 | Nginx gzip + immutable 静态缓存 | ✅ |
| 15 | 后端三级 Profile（dev/test/prod） | ✅ |
| 16 | CI 门禁（后端 89 测试 + 前端 build） | ✅ |
| 17 | Qodana 代码质量扫描 | ✅ |
| 18 | AOP 操作日志审计 | ✅ |
| 19 | Flyway 数据库版本化迁移 | ✅ |
| 20 | HTTPS + HTTP/2 Nginx 配置 | ✅ |

---

> 审查日期：2026-08-21 | 维护者：AI 助手 + hula0710
