# Industrial AI Hub — 综合审查与改进规划报告

> **项目版本**：v2.2.0 | **审查日期**：2026-08-21 ~ 2026-08-22 | **审查范围**：前后端 + 数据库 + 基础设施 + CI/CD + 硬件准备
>
> **对比基准**：ThingsBoard (22,288★) / JetLinks Community (6,583★) / youlai-boot (364★) / FastBee (2,270★)
>
> **综合完成度**：55% — 核心设备管理闭环已打通，但企业级能力（权限/多租户/备份/通知）大面积缺失。
>
> **本文档整合了**：全方位项目审查 + 数据库设计对比 + 31 项表设计缺陷 + 4 个 GitHub 优秀项目对比 + RBAC 升级方案 + Phase 5 硬件评估 + Phase 4 执行计划。

---

## 目录

- [第一章 项目结构与技术栈](#第一章-项目结构与技术栈)
- [第二章 安全审查（7 维度）](#第二章-安全审查7-维度)
- [第三章 性能与工程化审查（8 维度）](#第三章-性能与工程化审查8-维度)
- [第四章 企业产品功能完整性评估（7 维度）](#第四章-企业产品功能完整性评估7-维度)
- [第五章 数据库设计缺陷审查（31 项）](#第五章-数据库设计缺陷审查31-项)
- [第六章 GitHub 优秀项目对比](#第六章-github-优秀项目对比)
- [第七章 RBAC 权限体系升级方案](#第七章-rbac-权限体系升级方案)
- [第八章 Phase 5 硬件清单评估](#第八章-phase-5-硬件清单评估)
- [第九章 Phase 4 前修复执行计划](#第九章-phase-4-前修复执行计划)
- [第十章 风险优先级总矩阵](#第十章-风险优先级总矩阵)
- [第十一章 改进路线图（4 阶段）](#第十一章-改进路线图4-阶段)
- [附录 A：正面发现（已具备的良好实践）](#附录-a正面发现已具备的良好实践)
- [附录 B：参考项目源码索引](#附录-b参考项目源码索引)
- [附录 C：涉及的审查文件清单](#附录-c涉及的审查文件清单)

---

## 第一章 项目结构与技术栈

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
| 路由 | Vue Router | ^4.6.4 | Hash 模式 |
| UI 库 | Element Plus | ^2.14.3 | 按需引入 |
| 图标 | @element-plus/icons-vue | ^2.3.2 | ⚠️ 全量引入 280+ |
| HTTP | Axios | ^1.18.1 | 封装拦截器 |
| 图表 | ECharts + vue-echarts | ^6.1.0 / ^8.0.1 | Dashboard 可视化 |
| 构建工具 | Vite | ^8.1.1 | 固定端口 5173 |
| 状态管理 | ❌ 无 | — | localStorage 直存 token |
| 测试框架 | ❌ 无 | — | 前端零测试覆盖 |

### 1.3 后端技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java (JDK 25 LTS) | 25 |
| 框架 | Spring Boot | 3.5.0 |
| ORM | MyBatis Spring Boot | 3.0.5 |
| 数据库 | MySQL | 8.4 |
| 数据库迁移 | Flyway | BOM (V1-V3) |
| 缓存 | Spring Data Redis + Jedis + Redisson | ⚠️ 三客户端共存 |
| 消息队列 | RabbitMQ | 4.0-management |
| 认证 | JJWT | 0.12.6 |
| 密码加密 | spring-security-crypto | BCrypt |
| API 文档 | Knife4j | 4.5.0 |
| 限流 | Guava RateLimiter | 33.4.0-jre |
| 测试 DB | H2 | BOM (test scope) |

### 1.4 前后端通信方式

**纯 REST API（HTTP + JSON）**，无 GraphQL、无 WebSocket。

| 环节 | 细节 |
|------|------|
| 协议 | HTTP REST，前缀 `/api/` |
| 统一响应 | `ApiResponse<T>` → `{ code, message, data }` |
| 认证 | JWT Bearer Token（`Authorization` 头） |
| 跨域 | Vite proxy / Nginx 反代 / CORS 白名单 |
| 实时通信 | ❌ 无 WebSocket / SSE |

### 1.5 依赖包分析

**功能重复检查**：

| 领域 | 情况 | 结论 |
|------|------|------|
| Redis 客户端 | Jedis + Lettuce(Spring Data Redis) + Redisson | ⚠️ 功能重叠，建议生产环境统一为 Redisson |
| JSON 处理 | Jackson（Spring Boot 默认） | ✅ 无重复 |
| 日志框架 | Logback（Spring Boot 默认） | ✅ 无重复 |
| HTTP 客户端 | 仅 Axios（前端） | ✅ 无重复 |
| 校验 | Bean Validation（后端） | ✅ 无重复 |

**风险汇总**：

| 风险 | 详情 | 严重度 |
|------|------|--------|
| Redis 客户端三重叠加 | Jedis + Lettuce + Redisson | ⚠️ 中 |
| 无完整 Spring Security | 仅 spring-security-crypto，自定义拦截器 | ⚠️ 中 |
| nanoid 高危漏洞 | `nanoid <3.3.18`（CVSS 5.9） | 🔴 高 |

> 无已知高危漏洞依赖：JJWT 0.12.6、Guava 33.4.0、Spring Boot 3.5.0 均为当前最新稳定版。主要风险点：Redis 三客户端共存 + 自定义安全链（无 Spring Security 框架保护）+ 前端零测试覆盖。

---

## 第二章 安全审查（7 维度）

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
| **登录接口限流** | 🔴 高危 | `/api/auth/**` 被排除在限流外，无暴力破解防护 |
| **账户锁定机制** | 🔴 高危 | 连续失败登录不会触发锁定 |
| **Token 刷新机制** | 🔴 高危 | 无 refresh token，24h 过期后需重新登录 |
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

> **全部通过**：7 个 Mapper + 2 个 XML 全部使用 `#{}` 参数化查询，无 `${}` 拼接，无动态 ORDER BY / LIKE 注入。

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
// CorsConfig.java — 当前配置
config.setAllowedOriginPatterns(List.of(origins.split(",")));
config.addAllowedMethod("*");       // ⚠️ 通配方法
config.addAllowedHeader("*");       // ⚠️ 通配头
config.setAllowCredentials(true);   // ⚠️ 凭证模式
```

| 问题 | 风险 | 修复建议 |
|------|------|----------|
| AllowCredentials + 无 CSRF | ⚠️ 中危 | 关闭或加 CSRF |
| `allowedMethod("*")` | ⚠️ 中危 | 限制为 GET/POST/PUT/DELETE/OPTIONS |
| `allowedHeader("*")` | ⚠️ 低危 | 限制为 Authorization/Content-Type/Accept |
| 生产环境白名单 | ✅ 已支持 | 通过 CORS_ORIGINS 环境变量注入 |

> **CSRF 风险评估**：当前使用 Bearer Token（Authorization 头），不依赖 Cookie，传统 CSRF 攻击面较小。但 `AllowCredentials: true` 开启了凭证模式，若未来引入 Cookie 认证则风险立即升高。

---

## 第三章 性能与工程化审查（8 维度）

### 3.1 打包体积 — ⚠️ 多项缺失

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 路由级分割 | ✅ | 5 页面懒加载 |
| Vendor 分割 | ❌ | 未配置 `manualChunks` |
| **图标全量引入** | 🔴 高危 | `main.js` 注册 280+ 图标，多 ~50-80KB gzip |
| 构建时压缩 | ❌ | 无 `vite-plugin-compression` |
| Brotli 压缩 | ❌ | 未配置 |

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

> ECharts 已正确懒加载（~300KB 仅 Dashboard 页加载），但图标全量引入是首屏最大浪费。

### 3.3 图片资源 — ✅ 良好

仅 1 个 `favicon.svg`（12KB 矢量格式），无需优化。

### 3.4 缓存策略 — ✅ 基本良好

```nginx
# nginx.conf — 静态资源长缓存
location ~* \.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?)$ {
    expires 30d;
    add_header Cache-Control "public, immutable";  # ✅ hash 文件名
}
```

> ⚠️ **缺失**：`index.html` 未设置 `Cache-Control: no-cache`，用户可能拿到旧入口文件。

### 3.5 构建优化 — ⚠️ 配置精简

| 缺失项 | 优先级 |
|--------|--------|
| manualChunks | P1 |
| vite-plugin-compression | P1 |
| rollup-plugin-visualizer | P2 |
| drop console | P2 |

### 3.6 环境管理

| 环境 | 后端 | 前端 |
|------|------|------|
| dev | ✅ | ❌ 无 .env |
| test | ✅ H2 | ❌ |
| prod | ✅ | ❌ 无 .env.production |

> 🔴 前端无任何环境变量管理，`baseURL: '/api'` 硬编码。

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
| 结构化 JSON 日志 | ❌ | ❌ |
| 错误监控（Sentry） | ❌ | ❌ |
| 链路追踪 | ❌ | ❌ |
| 操作日志 | ✅ AOP 落库 | N/A |

---

## 第四章 企业产品功能完整性评估（7 维度）

### 4.1 核心业务模块

| 模块 | 前端 | 后端 API | 数据库表 | 闭环 |
|------|------|----------|----------|------|
| 认证管理 | Login.vue | 2 API | user/role/user_role | ✅ |
| 设备管理 | DeviceList + Detail | 5 API | device | ✅ |
| 设备数据 | Detail 内嵌 | 5 API | device_data | ✅ |
| 报警管理 | AlarmList.vue | 5 API | alarm | ⚠️ 通知未接入 |
| 操作日志 | LogList.vue | 3 API | operation_log | ✅ |
| 仪表盘 | Dashboard.vue | 复用 | — | ⚠️ 仅只读 |

### 4.2 功能缺失

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

| 页面 | 表单校验 | 操作反馈 | 加载状态 | 分页 | 空状态 | 搜索 |
|------|:--------:|:--------:|:--------:|:----:|:------:|:----:|
| Login | ⚠️ 手动 | ✅ | ✅ | — | — | — |
| Dashboard | N/A | ❌ | ❌ | — | 内联 | ❌ |
| DeviceList | ✅ rules | ✅ | ✅ | ✅ | ✅ | ✅ |
| AlarmList | N/A | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| LogList | N/A | ✅ | ✅ | ✅ | ✅ | ❌ |

### 4.4 数据展示

| 页面 | 关键词搜索 | 下拉筛选 | 日期范围 | 重置 | 导出 |
|------|:----------:|:--------:|:--------:|:----:|:----:|
| DeviceList | ✅ 设备名/编码 | ✅ 状态 | ❌ | ✅ | ❌ |
| AlarmList | ❌ | ✅ 状态 | ❌ | N/A | ❌ |
| OperationLogList | ❌ | ❌ | ❌ | N/A | ❌ |

> 🔴 **操作日志页面无任何筛选**：当 operation_log 表数据量增长后，查找特定操作的记录将非常困难。

### 4.5 权限体系 — ⚠️ 仅接口级

| 检查项 | 状态 |
|--------|------|
| 3 个固定角色 | ✅ ADMIN/OPERATOR/VIEWER |
| `@RequireRole` 接口级 | ✅ 全覆盖 |
| 角色管理 API | ❌ 完全缺失 |
| 菜单权限 | ❌ 前端菜单全可见 |
| 按钮级权限 | ❌ 无 v-permission |
| 数据权限 | ❌ 无行级隔离 |

### 4.6 国际化 / 多租户 — ❌ 零预留

| 检查项 | 国际化 | 多租户 |
|--------|--------|--------|
| 框架 | ❌ 无 vue-i18n | ❌ 无 tenant_id |
| 文本 | ❌ 硬编码中文 | ❌ 无隔离查询 |

### 4.7 数据备份 — ❌ 完全缺失

无备份表、无定时备份、无数据归档、无日志清理。

### 4.8 综合完成度

**模块级评分**：

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

## 第五章 数据库设计缺陷审查（31 项）

> 审查对象：3 个 Flyway 迁移脚本（V1/V2/V3）+ 7 个 Entity + 7 个 Mapper + 2 个 XML + AlarmRuleConfig

### 5.0 本项目当前表结构（缺陷对照参考）

**device 表**：

```sql
CREATE TABLE device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100),
    device_code VARCHAR(50) UNIQUE,         -- ❌ 与软删除冲突
    device_type ENUM('SENSOR','ACTUATOR','GATEWAY','CONTROLLER'),
    status TINYINT DEFAULT 1,               -- ❌ 默认"在线"（不合理）
    location VARCHAR(200),
    port INT,                               -- ❌ 类型过大（应为 SMALLINT UNSIGNED）
    is_deleted TINYINT DEFAULT 0
    -- ❌ 无 manufacturer/model/serial_number/firmware_version
    -- ❌ 无 device_profile_id（无模板概念）
    -- ❌ 无 parent_id（无父子设备）
    -- ❌ 无 tenant_id（无多租户）
);
```

**alarm 表**：

```sql
CREATE TABLE alarm (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT,
    alarm_type VARCHAR(32),       -- ❌ 无 CHECK 约束
    alarm_level TINYINT,
    status TINYINT DEFAULT 0,    -- 0未处理/1已确认/2已解决
    message VARCHAR(500),
    triggered_at DATETIME,
    resolved_at DATETIME,
    created_at DATETIME,
    -- ❌ 无 acknowledged_at / acknowledged_by
    -- ❌ 无 resolved_by / updated_at
    -- ❌ 无 rule_id / actual_value / threshold_value
    -- ❌ 无 assignee_id / propagate 传播机制
);
```

**device_data 表**：

```sql
CREATE TABLE device_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT,
    data_type VARCHAR(32),
    value DECIMAL(18,4),
    unit VARCHAR(16) DEFAULT NULL,   -- ❌ 允许 NULL
    recorded_at DATETIME,
    created_at DATETIME
    -- ❌ 无分区 / 无 TTL / 无防重复唯一约束
    -- ❌ value 仅 DECIMAL，不支持字符串/布尔/JSON 类型
);
```

**本项目索引问题**：

```sql
-- 冗余索引 1：被唯一键 uk_user_role(user_id, role_id) 最左前缀覆盖
KEY idx_user_id (user_id)  -- ❌ 冗余

-- 冗余索引 2：被复合索引 idx_device_type_time(device_id, data_type, recorded_at) 最左前缀覆盖
KEY idx_device_id (device_id)  -- ❌ 冗余

-- 低选择性索引：is_deleted 只有 0/1 两个值
KEY idx_is_deleted (is_deleted)  -- ❌ 优化器不会使用
```

### 🔴 高危缺陷（5 项）

#### 缺陷 1 — V2 测试种子数据在 Flyway 迁移目录中

`V2__seed_test_data.sql` 含 20 个测试用户、50 台测试设备，Flyway 会在**生产环境**自动执行。

**修复**：移到 `db/migration/dev/`，通过 Profile 隔离。

#### 缺陷 2 — alarm 表缺少确认人/确认时间字段

```java
// 当前 — 不记录谁操作、何时操作
@Update("UPDATE alarm SET status=1 WHERE id=#{id}")
int acknowledge(@Param("id") Long id);

// 修复后
@Update("UPDATE alarm SET status=1, acknowledged_at=NOW(), " +
        "acknowledged_by=#{userId} WHERE id=#{id}")
int acknowledge(@Param("id") Long id, @Param("userId") Long userId);
```

**需新增字段**：`acknowledged_at`、`acknowledged_by`、`resolved_by`、`updated_at`

#### 缺陷 3 — 唯一约束与软删除冲突

`device.uk_device_code`、`user.uk_username`、`user.uk_email` 在软删除后仍生效，无法创建同编码新记录。

**修复**：组合唯一约束 `(device_code, is_deleted)` 或生成列方案。

#### 缺陷 4 — device_data 表无数据保留策略

50 台设备 × 5 种数据类型 × 每分钟采集 = 1.3 亿条/年，无分区、无 TTL、无归档。

**修复**：按月分区 + 定时归档/清理。

#### 缺陷 5 — operation_log 表无数据增长管理

`findRecent()` 无 WHERE 条件全表扫描后取 LIMIT 100。

**修复**：按月分区 + 查询添加时间范围。

### ⚠️ 中危缺陷（14 项）

| # | 缺陷 | 涉及表 | 修复方向 |
|---|------|--------|----------|
| 6 | 全表无外键约束 | 全部 | 添加 FK 约束 |
| 7 | role 表缺少 is_deleted/status/updated_at | role | 补齐三个字段 |
| 8 | user_role 缺少 created_at/created_by | user_role | 添加审计字段 |
| 9 | 全表缺少 created_by/updated_by 和 version | 全部 | 审计字段 + 乐观锁 |
| 10 | alarm_type 缺少 CHECK 约束 | alarm | 添加 CHECK 覆盖 9 种类型 |
| 11 | chk_target_type 未同步扩展 | operation_log | 补充 DEVICE_DATA 等 |
| 12 | device_data 缺少防重复唯一约束 | device_data | `(device_id, data_type, recorded_at)` 唯一索引 |
| 13 | 报警规则硬编码在 Java 中 | alarm | 创建 `alarm_rule` 表 |
| 14 | 枚举值 CHECK 硬编码 | 全部 | 引入 `sys_dict` 字典表 |
| 15 | alarm 状态流转无约束 | alarm | 触发器或应用层状态机 |
| 16 | user_role 物理删除与软删除矛盾 | user_role | 不删除，查询时过滤 |
| 17 | 软删除策略不一致 | role/alarm | 统一软删除 |
| 18 | AlarmRuleConfig 类型与表注释不一致 | alarm | 更新注释 |
| 19 | operation_log 缺少 target_type 索引 | operation_log | 添加索引 |

### 🟢 低危缺陷（12 项）

| # | 缺陷 | 说明 |
|---|------|------|
| 20 | `device.port` 用 INT | 应为 SMALLINT UNSIGNED |
| 21 | user 缺少 last_login_at | 无法做安全审计 |
| 22 | device 缺少设备元数据字段 | 缺 manufacturer/model/serial_number/firmware_version |
| 23 | user_role.idx_user_id 冗余索引 | 被唯一键最左前缀覆盖 |
| 24 | device_data.idx_device_id 冗余索引 | 被复合索引最左前缀覆盖 |
| 25 | is_deleted 索引选择性极低 | 仅 0/1 两个值 |
| 26 | alarm 缺少 (device_id, status) 复合索引 | — |
| 27 | role 缺少 role_name 唯一约束 | — |
| 28 | device_data.unit 允许 NULL | 每条数据都应有单位 |
| 29 | device.status 默认值为"在线" | 新建设备应默认离线 |
| 30 | 无多租户支持 | 7 张表均无 tenant_id |
| 31 | 无预留扩展字段 | 缺 remarks/extra(JSON) |

---

## 第六章 GitHub 优秀项目对比

### 6.1 四个参考项目总览

| 维度 | ThingsBoard | JetLinks | youlai-boot | FastBee |
|------|-------------|----------|-------------|---------|
| Star | 22,288 | 6,583 | 364 | 2,270 |
| 领域 | IoT 设备管理 | IoT 设备管理 | 权限管理 | IoT 物联网平台 |
| 技术栈匹配 | 低（Angular/PG） | 中（SB3 但响应式） | ✅ 高（几乎一致） | ⚠️ 低（SB 2.x/若依） |
| License | Apache-2.0 | Apache-2.0 | Apache-2.0 | ⚠️ AGPL-3.0 |
| 数据库 | PostgreSQL + Cassandra | PostgreSQL + ES/TDengine | MySQL | MySQL + TDengine |
| 前端 | Angular | Vue + Ant Design | Vue 3 + Element Plus | Vue 2/3 + Element-UI |
| 多租户 | ✅ tenant_id 列 | ✅ 维度资产模型 | ❌ | ✅ tenant_id |
| 物模型 | ✅ JSONB | ✅ JSON metadata | ❌ | ✅ 属性/功能/事件 |
| 设备影子 | ❌ | ❌ | ❌ | ✅ is_shadow |
| 规则引擎 | ✅ 规则链可视化 | ✅ 场景联动 | ❌ | ✅ 规则脚本 |
| AI 模块 | ❌ | ❌ | ❌ | ✅ NL2SQL + 知识库 |
| 移动端 | ❌ | ❌ | ❌ | ✅ UniApp 全平台 |

### 6.2 数据库设计核心对比

#### 主键设计

| 维度 | 本项目 | ThingsBoard | JetLinks | youlai-boot | FastBee |
|------|--------|-------------|----------|-------------|---------|
| 类型 | BIGINT 自增 | UUID | String（雪花） | bigint 自增 | bigint 自增 |
| 分布式 | ❌ | ✅ | ✅ | ❌ | ❌ |
| 可自定义 | ❌ | ❌ | ✅ 支持 SN | ❌ | ❌ |

#### 多租户设计

| 维度 | 本项目 | ThingsBoard | JetLinks | FastBee |
|------|--------|-------------|----------|---------|
| 支持 | ❌ | ✅ tenant_id 列 | ✅ 维度资产 | ✅ tenant_id + tenant_name |
| 侵入性 | — | 每张表都有 | 不侵入表结构 | 每张表都有 |

#### 设备管理

| 维度 | 本项目 | ThingsBoard | JetLinks | FastBee |
|------|--------|-------------|----------|---------|
| 设备模板 | ❌ | ✅ device_profile | ✅ dev_product | ✅ iot_product |
| 物模型 | ❌ | JSONB profile_data | JSON metadata | JSON things_model |
| 父子设备 | ❌ | ✅ relation 表 | ✅ parent_id | ❌ |
| 设备标签 | ❌ | ✅ attribute_kv | ✅ dev_device_tags | ✅ 设备属性 |
| OTA 固件 | ❌ | ✅ ota_package | ✅ firmware | ✅ 固件版本 |

#### 告警表

| 维度 | 本项目 | ThingsBoard | JetLinks | FastBee |
|------|--------|-------------|----------|---------|
| 确认人 | ❌ | ✅ assignee_id | ✅ creator_id | ✅ |
| 确认时间 | ❌ | ✅ ack_ts | ✅ create_time | ✅ |
| 告警评论 | ❌ | ✅ alarm_comment 分区 | ✅ 告警历史 | ✅ |
| 告警传播 | ❌ | ✅ entity_alarm + propagate | ✅ 场景联动 | ❌ |
| 规则关联 | ❌ | ✅ 规则链触发 | ✅ alarm_config | ✅ 规则脚本 |

#### 时序数据

| 维度 | 本项目 | ThingsBoard | JetLinks | FastBee |
|------|--------|-------------|----------|---------|
| 存储引擎 | MySQL 单表 | PG 分区/Cassandra | ES/TDengine | TDengine/IoTDB |
| 分区 | ❌ | ✅ 声明式分区 | ✅ ES 按月分索引 | ✅ 时序DB 内置 |
| TTL 清理 | ❌ | ✅ 存储过程自动 | ✅ ES ILM | ✅ 时序DB TTL |
| 最新值缓存 | ❌ | ✅ ts_kv_latest + 乐观锁 | ✅ 同步到 DB | ✅ |
| 多类型值 | ❌ 仅 DECIMAL | ✅ 5 列多类型 | ✅ ES 多类型 | ✅ |

#### 权限模型

| 维度 | 本项目 | ThingsBoard | JetLinks | youlai-boot | FastBee |
|------|--------|-------------|----------|-------------|---------|
| 模型 | 3 固定角色 | 3 级 Authority | RBAC + 维度资产 | **四级 RBAC** | 若依 RBAC |
| 菜单权限 | ❌ | ✅ PE 版 | ✅ | ✅ sys_menu 三级 | ✅ 若依菜单 |
| 按钮权限 | ❌ | ✅ PE 版 | ✅ | ✅ perm 字段 | ✅ 若依按钮 |
| 数据权限 | ❌ | ✅ tenant_id | ✅ 维度资产 | ✅ 5 级 data_scope | ✅ 若依数据权限 |

#### 审计字段

| 维度 | 本项目 | ThingsBoard | JetLinks | youlai-boot | FastBee |
|------|--------|-------------|----------|-------------|---------|
| 创建时间 | ✅ created_at | ✅ created_time（bigint） | ✅ create_time（Long） | ✅ create_time | ✅ create_time |
| 更新时间 | ❌ 仅部分 | ❌ version 替代 | ✅ modify_time | ✅ update_time | ✅ update_time |
| 创建人 | ❌ | ❌ audit_log | ✅ creator_id | ✅ create_by | ✅ create_by |
| 更新人 | ❌ | ❌ | ✅ modifier_id | ✅ update_by | ✅ update_by |
| 乐观锁 | ❌ | ✅ version | ❌ | ❌ | ❌ |

#### 操作日志对比

| 维度 | 本项目 | ThingsBoard | youlai-boot |
|------|--------|-------------|-------------|
| 表名 | `operation_log` | `audit_log`（分区表） | `sys_log` |
| 分区 | ❌ 无 | ✅ 按 `created_time` 分区 | ❌ 无 |
| 请求信息 | ❌ 无 | ✅ `action_data` | ✅ `request_uri`/`request_method`/`request_params`/`response_content` |
| 客户端环境 | ❌ 无 | ❌ 无 | ✅ `ip`/`province`/`city`/`browser`/`os` |
| 性能数据 | ❌ 无 | ❌ 无 | ✅ `execution_time`（ms） |
| 操作人 | ❌ 无 `user_id` | ✅ `user_id` + `user_name` | ✅ `create_by` |
| 增长管理 | ❌ 无 | ✅ 分区 DROP | ❌ 无 |

#### 软删除策略对比

| 表 | 本项目 | ThingsBoard | JetLinks | youlai-boot |
|----|--------|-------------|----------|-------------|
| user | ✅ `is_deleted` | 物理删除 | 物理删除 | ✅ `is_deleted` |
| device | ✅ `is_deleted` | 物理删除 | 物理删除 | N/A |
| role | ❌ 物理删除 | N/A | 物理删除 | ✅ `is_deleted` |
| alarm | ❌ 物理删除 | 物理删除（状态管理） | 物理删除 | N/A |
| operation_log | ❌ 物理删除 | ✅ 分区删除 | 物理删除 | ❌ 物理删除 |

> 本项目软删除策略不一致（部分表有 `is_deleted`，部分没有），且唯一约束与软删除冲突——软删除后无法复用编码。

#### 索引设计对比

| 维度 | 本项目 | ThingsBoard | JetLinks | youlai-boot |
|------|--------|-------------|----------|-------------|
| 索引数量 | 12 个 | 20+ 个 | 7 个 | 8 个 |
| 冗余索引 | 🔴 2 个 | ✅ 无 | ✅ 无 | ✅ 无 |
| 复合索引 | ✅ 有 | ✅ 大量 | ✅ 有 | ✅ 有 |
| 部分索引 | ❌ 不支持（MySQL） | ✅ `WHERE cleared=false` | ❌ 不支持 | ❌ 不支持 |
| 覆盖索引 | ❌ 无 | ✅ `INCLUDE(alarm_id)` | ❌ 无 | ❌ 无 |
| 索引前导列策略 | ❌ 无规律 | ✅ `tenant_id` 始终前导 | ✅ 查询驱动 | ❌ 无规律 |

### 6.3 各项目最佳学习方向

| 来源 | 最佳学习方向 | 具体内容 |
|------|-------------|----------|
| **ThingsBoard** | IoT 领域架构 + 时序数据 | UUID 主键、tenant_id 隔离、声明式分区、部分索引、version 乐观锁、规则链、告警传播 |
| **JetLinks** | Java IoT 平台 + 物模型 | 雪花 ID + 可自定义、物模型 JSON 存储、维度资产权限、父子设备、审计字段 updatable=false |
| **youlai-boot** | 技术栈工程实践（可直接照搬） | 四级 RBAC、sys_menu 三级菜单、perm 按钮权限、5 级 data_scope、sys_dict 字典表、JWT Redis 管理 |
| **FastBee** | IoT 业务设计 + AI 集成灵感 | 物模型三分类、设备影子、设备四态状态机、规则引擎+场景自动化、AI NL2SQL、Web 组态 |

### 6.4 FastBee 特殊风险提示

| 风险 | 说明 |
|------|------|
| ⚠️ AGPL-3.0 强传染性 | 商用需授权，不能直接复制代码 |
| ⚠️ Spring Boot 2.x | 基于 javax.*，本项目是 jakarta.*，不可直接移植 |
| ⚠️ 深度耦合若依框架 | 迁移成本高于自建 |
| ⚠️ 无 Flyway | Navicat dump 管理数据库 |
| ⚠️ MySQL 5.7 基线 | SQL dump 基于 5.7.44 |

**结论**：FastBee 适合作为**概念参考和设计灵感来源**（物模型、设备影子、规则引擎、AI/NL2SQL），但**不建议直接复制代码**。

### 6.5 关键参考代码示例

#### ThingsBoard 告警表（完整字段参考）

```sql
CREATE TABLE alarm (
    id uuid PRIMARY KEY,
    created_time bigint NOT NULL,
    ack_ts bigint,              -- 确认时间戳
    clear_ts bigint,            -- 清除时间戳
    start_ts bigint,            -- 告警开始时间
    end_ts bigint,              -- 告警结束时间
    assign_ts bigint DEFAULT 0, -- 分配时间
    originator_id uuid,         -- 告警源实体 ID
    originator_type integer,
    tenant_id uuid,
    customer_id uuid,
    assignee_id uuid,           -- 处理人
    acknowledged boolean,       -- 是否已确认
    cleared boolean,            -- 是否已清除
    severity varchar(255),       -- CRITICAL/MAJOR/MINOR/WARNING/INDETERMINATE
    type varchar(255),           -- 告警类型
    additional_info varchar,    -- 附加信息（实际值、阈值等）
    propagate boolean,           -- 是否传播
    propagate_relation_types varchar,
    propagate_to_owner boolean,
    propagate_to_tenant boolean
);
```

#### ThingsBoard 索引设计亮点

```sql
-- 部分索引：只索引未清除的告警（活跃数据）
CREATE INDEX idx_alarm_originator_alarm_type_active
    ON alarm USING btree (originator_id, type) WHERE cleared = false;

-- 覆盖索引：INCLUDE 避免回表
CREATE INDEX idx_entity_alarm_entity_id_alarm_type_created_time_alarm_id ON entity_alarm
USING btree (tenant_id, entity_id, alarm_type, created_time DESC) INCLUDE(alarm_id);

-- tenant_id 始终为前导列
CREATE INDEX idx_device_customer_id ON device(tenant_id, customer_id);
CREATE INDEX idx_alarm_tenant_created_time ON alarm(tenant_id, created_time DESC);
```

#### JetLinks 审计字段实现

```java
// 通过接口契约实现统一审计
public class DeviceInstanceEntity extends GenericEntity<String>
    implements RecordCreationEntity, RecordModifierEntity {

    @Column(updatable = false)  // 创建者只读，不可更新
    private String creatorId;
    @Column(updatable = false)
    private String creatorName;
    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)  // 自动填充
    private Long createTime;
    private String modifierId;
    private String modifierName;
    @DefaultValue(generator = Generators.CURRENT_TIME)
    private Long modifyTime;
}
```

#### youlai-boot 菜单权限表设计（直接参考）

```sql
CREATE TABLE sys_menu (
    id bigint AUTO_INCREMENT PRIMARY KEY,
    parent_id bigint,              -- 父菜单 ID
    tree_path varchar(255),        -- 路径冗余（如 "0,1,210"），避免递归查树
    type char(1),                  -- C目录 / M菜单 / B按钮
    name varchar(64),
    perm varchar(128),             -- 按钮权限标识：sys:user:create
    component varchar(128),        -- Vue 组件路径：system/user/index
    route_name varchar(64),
    route_path varchar(128),
    icon varchar(64),
    sort int,
    visible tinyint,
    keep_alive tinyint
    -- 后端按菜单动态生成路由表下发前端
);
```

#### youlai-boot 字典表设计

```sql
-- 类型表
CREATE TABLE sys_dict (
    id bigint AUTO_INCREMENT PRIMARY KEY,
    dict_code varchar(50) UNIQUE,  -- 如 gender
    name varchar(50),
    status tinyint,
    is_deleted tinyint DEFAULT 0
);

-- 项表
CREATE TABLE sys_dict_item (
    id bigint AUTO_INCREMENT PRIMARY KEY,
    dict_code varchar(50),          -- 逻辑关联（非外键）
    value varchar(50),
    label varchar(100),
    tag_type varchar(50),           -- 前端样式：success/warning/danger
    sort int
);
```

### 6.6 值得借鉴的设计模式汇总

#### 从 ThingsBoard 借鉴

| # | 设计模式 | 本项目应用建议 |
|---|----------|----------------|
| 1 | UUID 主键 + relation 通用关系表 | 如果未来需要设备-设备关联，可参考 |
| 2 | tenant_id 列 + 索引前导列 | 多租户改造时直接参考 |
| 3 | 声明式分区 + TTL 清理 | device_data 和 operation_log 分区 |
| 4 | 部分索引（WHERE 条件索引） | MySQL 8.0+ 不支持，可用生成列模拟 |
| 5 | version 乐观锁 | 核心表添加 version 字段 |
| 6 | KV 多类型值列（bool_v/str_v/long_v/dbl_v） | device_data 表重构时参考 |
| 7 | Key 字典压缩 | device_data 高频写入时优化 |
| 8 | 告警传播机制 | 多设备关联告警场景参考 |
| 9 | Profile 模板模式 | 设备模板功能参考 |
| 10 | 混合存储架构 | 时序数据迁移到专用存储时参考 |

#### 从 JetLinks 借鉴

| # | 设计模式 | 本项目应用建议 |
|---|----------|----------------|
| 1 | 雪花算法 String 主键 + 可自定义 | 设备 ID 支持用 SN |
| 2 | 物模型 JSON 存储 | 简化设备属性管理 |
| 3 | 维度-资产权限模型 | 多维度数据权限参考 |
| 4 | EasyORM 自动建表 | 不建议照搬，Flyway 更可控 |
| 5 | store_policy 可插拔存储 | 时序数据多策略存储参考 |
| 6 | 父子设备 parent_id | 网关-子设备关系 |
| 7 | 审计字段 updatable=false | ORM 层防篡改创建者信息 |
| 8 | 设备标签独立表 | 灵活的设备属性管理 |

#### 从 youlai-boot 借鉴

| # | 设计模式 | 本项目应用建议 |
|---|----------|----------------|
| 1 | 四级 RBAC（用户-角色-菜单-部门） | 权限体系升级直接参考 |
| 2 | sys_menu 三级（目录/菜单/按钮） | 前端菜单权限直接参考 |
| 3 | perm 权限标识 `模块:资源:操作` | 按钮级权限指令参考 |
| 4 | 5 级 data_scope 数据权限 | 数据权限实现参考 |
| 5 | tree_path 路径冗余 | 部门/菜单树形查询优化 |
| 6 | sys_dict 字典表 | 替代 CHECK 约束 |
| 7 | JWT 纯 Redis 管理（不落库） | Token 黑名单参考 |
| 8 | sys_log 操作日志字段设计 | 操作日志表字段补全参考 |

---

## 第七章 RBAC 权限体系升级方案

### 7.1 现状问题

```
当前：3 个硬编码角色（ADMIN/OPERATOR/VIEWER）
      注册默认 VIEWER，无 API 可变更角色
      无菜单/按钮权限控制
      无验证码、无忘记密码、无第三方验证
      无水平越权防护
```

### 7.2 分步升级方案

#### 第一步：Phase 4 前必须做（~1 天）

| 修复项 | 具体做法 | 参考项目 |
|--------|----------|----------|
| 角色 CRUD API | RoleController 5 个接口 | youlai-boot |
| 用户角色分配 | `PUT /api/users/{id}/role` | youlai-boot |
| 前端菜单权限 | 路由 meta.roles + `beforeEach` 守卫 | youlai-boot |
| 前端按钮权限 | `v-permission` 自定义指令 | youlai-boot |

#### 第二步：Phase 4 中穿插做

| 增强项 | 优先级 | 说明 |
|--------|--------|------|
| 登录图形验证码 | P1 | 后端生成 → Redis 存 3 分钟 → 前端提交校验 |
| 忘记密码（邮箱重置） | P1 | 发送重置链接 → 点击 → 修改密码 |
| 短信验证 | P2 | 需接入阿里云/腾讯云 SMS |
| TOTP Authenticator | P3 | 企业级才需要 |

### 7.3 youlai-boot 四级 RBAC 模型（直接参考）

```
sys_user ──(sys_user_role)── sys_role ──(sys_role_menu)── sys_menu (目录C/菜单M/按钮B)
                                  │
                                  └──(sys_role_dept)── sys_dept (树形部门)

数据权限 5 级：
1-所有数据  2-部门及子部门  3-本部门  4-本人  5-自定义部门

菜单表核心字段：
- type: C目录 / M菜单 / B按钮
- perm: 按钮权限标识（sys:user:create）
- component: Vue 组件路径
- tree_path: 路径冗余避免递归查树
```

### 7.4 认证增强优先级

| 认证方式 | 优先级 | 工作量 | 说明 |
|----------|--------|--------|------|
| 图形验证码 | P1 | 1 天 | 最实用，防暴力破解 |
| 忘记密码 | P1 | 0.5 天 | 邮箱重置链接 |
| 短信验证 | P2 | 2 天 | 需接入 SMS 服务商 |
| TOTP | P3 | 2 天 | Google Authenticator |

---

## 第八章 Phase 5 硬件清单评估

### 8.1 已采购硬件

| 设备 | 数量 | 用途 | 匹配度 |
|------|:----:|------|:------:|
| ESP32-S3-N16R8 | 2 | 主控板（16MB Flash + 8MB PSRAM + WiFi/BLE） | ✅ 完美 |
| DHT22 温湿度传感器 | 2 | 温度 + 湿度采集 | ✅ 匹配报警规则 |
| GY-302 BH1750 光照度 | 1 | 光照强度采集 | ✅ 良好 |
| 0.96 OLED 显示屏 | 2 | 本地数据显示 | ✅ 良好 |
| 9205 数字万用表 | 1 | 调试工具 | ✅ 实用 |
| Arduino 学习套件 | 1 | 基础学习 + 跳线/面包板 | ✅ 必需 |
| 1 路继电器模块 | 1 | 执行器控制 | ✅ 良好 |
| USB Type-C 线 | 2 | 供电 + 烧录 | ✅ 必需 |

### 8.2 评估结论：**足够满足 Phase 5 MQTT 学习**

完整覆盖 IoT 数据链路：

```
传感器采集 → ESP32 处理 → MQTT 上报 → 后端接收 → 报警检测 → 继电器控制
    ✅           ✅         ✅          ✅         ✅         ✅
```

### 8.3 可验证的业务场景

| 场景 | 传感器 | 执行器 | 报警规则 |
|------|--------|--------|----------|
| 温度监控 | DHT22 #1 | — | OVER_TEMP > 85°C |
| 湿度监控 | DHT22 #1 | — | OVER_HUMIDITY > 90% |
| 光照监控 | BH1750 | 继电器(开关灯) | 可新增 LIGHT 规则 |
| 本地显示 | OLED | — | 实时显示传感器值 |
| 双设备对比 | DHT22 #1 vs #2 | — | 同房间两传感器对比 |

### 8.4 建议补充（非必须）

| 设备 | 价格 | 是否必须 |
|------|:----:|:--------:|
| BMP280/BME280 气压传感器 | ~5 元 | ❌ 非必须 |
| 继电器再买 1 个 | ~8 元 | ❌ 非必须 |
| DS18B20 防水温度传感器 | ~5 元 | ❌ 非必须 |

---

## 第九章 Phase 4 前修复执行计划

### Day 0（半天）— 数据库止血

| 任务 | Flyway 脚本 | 工作量 |
|------|-------------|--------|
| V2 测试数据移出 Flyway 目录 | 移到 `db/migration/dev/` | 0.5h |
| alarm 表补审计字段 | `V4__alarm_audit_fields.sql` | 1h |
| 修复唯一约束与软删除冲突 | `V5__fix_unique_constraint.sql` | 0.5h |
| role 表补 status/is_deleted/updated_at | `V6__role_enhance.sql` | 0.5h |

### Day 1（1 天）— RBAC 最小可用

| 任务 | 具体内容 |
|------|----------|
| RoleController CRUD | 5 个接口（list/create/update/delete/getById） |
| 用户角色分配 | `PUT /api/users/{id}/role` |
| 前端路由 meta.roles | 路由守卫检查角色 |
| 前端 v-permission 指令 | 无权按钮隐藏 |

### Day 2（1 天）— 前端最小修复 + 安全加固

| 任务 | 具体内容 |
|------|----------|
| 图标按需引入 | `main.js` 改为显式 import ~10 个图标 |
| 前端环境变量 | `.env.development` / `.env.production` |
| 登录图形验证码 | 后端生成 + Redis 存取 + 前端展示 |
| npm audit fix | 修复 nanoid 漏洞 |

### Phase 4 进行中 — 每周抽 1-2 小时

| 任务 | 优先级 |
|------|--------|
| alarm_rule 表 + 规则持久化 | P1 |
| device_data 表分区 | P1 |
| sys_dict 字典表 | P2 |
| 忘记密码（邮箱重置） | P2 |
| 前端构建优化（manualChunks + compression） | P2 |

---

## 第十章 风险优先级总矩阵

### 🔴 P0 — 立即修复（阻塞 Phase 4）

| # | 问题 | 维度 | 修复方案 | 参考项目 |
|---|------|------|----------|----------|
| 1 | 水平越权 — Device/DeviceData/Alarm 无所有权校验 | 接口安全 | Service 层增加 userId 归属校验 | ThingsBoard |
| 2 | 登录接口无限流 + 无账户锁定 | 认证鉴权 | IP 限流 + 失败锁定 | — |
| 3 | 无 Token 刷新/黑名单机制 | 认证鉴权 | refresh token + Redis 黑名单 | youlai-boot |
| 4 | nanoid 高危漏洞 | 依赖安全 | `npm audit fix` | — |
| 5 | 图标全量引入 | 打包体积 | 按需 import | — |
| 6 | 角色管理 API 缺失 | 功能完整性 | 角色 CRUD + 权限分配 | youlai-boot |
| 7 | 用户角色分配缺失 | 功能完整性 | 用户-角色关联 API | youlai-boot |
| 8 | V2 测试数据污染生产环境 | 数据库 | 移出 Flyway 目录 | — |
| 9 | alarm 表缺确认人/确认时间 | 数据库 | 补齐 4 个审计字段 | ThingsBoard |
| 10 | 唯一约束与软删除冲突 | 数据库 | 组合唯一约束 | — |
| 11 | device_data 无分区无限增长 | 数据库 | 按月分区 + TTL | ThingsBoard |
| 12 | 前端环境变量缺失 | 环境管理 | .env.development / .env.production | — |

### 🟡 P1 — 尽快修复（Phase 4 中穿插）

| # | 问题 | 修复方案 | 参考项目 |
|---|------|----------|----------|
| 1 | Token 黑名单/吊销 | Redis 维护黑名单 | youlai-boot |
| 2 | CSRF + AllowCredentials | 关闭 AllowCredentials | — |
| 3 | DTO 校验补全 | @Size/@Pattern/@Min/@Max | — |
| 4 | CORS 通配方法/头 | 限制具体方法和头 | — |
| 5 | Vendor 分包 | manualChunks | — |
| 6 | 构建时压缩 | vite-plugin-compression | — |
| 7 | index.html no-cache | Nginx 配置 | — |
| 8 | 前端错误监控 | Sentry | — |
| 9 | CD 流水线 | GitHub Actions 自动部署 | — |
| 10 | 后端结构化日志 | logback-spring.xml | — |
| 11 | 报警规则持久化 | alarm_rule 表 + CRUD | JetLinks/FastBee |
| 12 | 报警通知接入 | 钉钉/邮件 | FastBee |
| 13 | operation_log 分区 | 按月分区 | ThingsBoard |
| 14 | role 表补齐字段 | status/is_deleted/updated_at | youlai-boot |
| 15 | 菜单/按钮/路由权限 | sys_menu + v-permission | youlai-boot |
| 16 | 登录图形验证码 | Redis 存取 | — |

### 🔵 P2 — 计划修复

| # | 问题 | 参考项目 |
|---|------|----------|
| 1 | sys_dict 字典表 | youlai-boot |
| 2 | 忘记密码（邮箱重置） | — |
| 3 | 批量操作 | — |
| 4 | 导出功能 | — |
| 5 | Dashboard loading + 自动刷新 | — |
| 6 | 操作日志高级筛选 | youlai-boot |
| 7 | 设备元数据字段 | ThingsBoard |
| 8 | version 乐观锁 | ThingsBoard |
| 9 | 防重复唯一约束（device_data） | — |
| 10 | 冗余索引清理 | — |
| 11 | 定时数据库备份 | — |
| 12 | 部署回滚机制 | — |

### 🟢 P3 — 长期规划

| # | 问题 | 工作量 |
|---|------|--------|
| 1 | 多租户隔离 | 40h |
| 2 | 国际化 i18n | 32h |
| 3 | 设备模板/物模型 | 16h |
| 4 | WebSocket 实时推送 | 16h |
| 5 | 蓝绿/灰度部署 | 16h |
| 6 | ELK 日志采集 | 16h |
| 7 | AI NL2SQL 集成 | 16h |
| 8 | Web 组态可视化 | 16h |
| 9 | 移动端 UniApp | 40h |

---

## 第十一章 改进路线图（4 阶段）

### 阶段一：Phase 4 前止血（3 天）

| Day | 任务 | 工作量 | 参考项目 |
|-----|------|--------|----------|
| 0 | 数据库止血（V4/V5/V6 + V2 移出） | 2.5h | — |
| 1 | RBAC 最小可用（角色 CRUD + 菜单权限 + 按钮权限） | 8h | youlai-boot |
| 2 | 前端修复（图标 + 环境变量 + 验证码 + npm fix） | 4h | — |

### 阶段二：Phase 4 AI 集成（按 DAILY_ROADMAP）

正常进入 Day 64 → OpenAI API 基础，每周抽 1-2 小时修复 P1 缺陷。

**Phase 4 中穿插的数据库改进任务**：

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 1 | alarm 表补齐 acknowledged_at/by、resolved_by | ThingsBoard | 4h |
| 2 | 修复唯一约束与软删除冲突 | — | 2h |
| 3 | device_data 表分区 + 归档策略 | ThingsBoard | 8h |
| 4 | operation_log 表分区 | ThingsBoard | 4h |
| 5 | V2 测试数据移出 Flyway 目录 | — | 1h |
| 6 | role 表补齐 status/is_deleted/updated_at | youlai-boot | 2h |
| 7 | 创建 alarm_rule 表，规则持久化 | JetLinks | 8h |

### 阶段三：Phase 5 MQTT 硬件联调

使用已采购硬件验证完整 IoT 数据链路：
- ESP32-S3 + DHT22 + BH1750 → MQTT 上报 → 后端接收 → 报警检测 → 继电器控制
- OLED 本地显示传感器值
- 双设备对比测试

**Phase 5 前后的权限体系升级任务**：

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 8 | 角色 CRUD API | youlai-boot | 8h |
| 9 | 用户角色分配 API | youlai-boot | 4h |
| 10 | sys_menu 菜单权限表 | youlai-boot | 8h |
| 11 | 按钮级权限指令 v-permission | youlai-boot | 4h |
| 12 | 5 级数据权限 data_scope | youlai-boot | 8h |
| 13 | 路由守卫角色检查 | youlai-boot | 2h |
| 14 | sys_dict 字典表 | youlai-boot | 4h |

### 阶段四：SaaS 化准备（Phase 5 后）

**设备模型升级**：

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 15 | device_profile 设备模板表 | ThingsBoard | 8h |
| 16 | 物模型 JSON 存储 | JetLinks | 8h |
| 17 | 父子设备 parent_id | JetLinks | 4h |
| 18 | 设备标签独立表 | JetLinks | 4h |
| 19 | 设备元数据字段（manufacturer/model/serial_number） | ThingsBoard | 2h |
| 20 | version 乐观锁 | ThingsBoard | 4h |

**多租户改造**：

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 21 | 所有业务表添加 tenant_id | ThingsBoard | 16h |
| 22 | 唯一约束改为 (tenant_id, xxx) | ThingsBoard | 8h |
| 23 | 索引以 tenant_id 为前导列 | ThingsBoard | 8h |
| 24 | 租户管理 API + 租户配额 | ThingsBoard | 16h |

**其他 SaaS 化能力**：

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 25 | 国际化 i18n | — | 32h |
| 26 | WebSocket 实时推送 | — | 16h |
| 27 | AI NL2SQL 集成 | FastBee | 16h |
| 28 | 蓝绿/灰度部署 | — | 16h |
| 29 | ELK 日志采集 | — | 16h |

---

## 附录 A：正面发现（已具备的良好实践）

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

## 附录 B：参考项目源码索引

| 项目 | 仓库 | 关键参考路径 |
|------|------|-------------|
| ThingsBoard | `thingsboard/thingsboard` | `dao/src/main/resources/sql/schema-entities.sql` |
| JetLinks | `jetlinks/jetlinks-community` | `jetlinks-manager/device-manager/.../entity/` |
| youlai-boot | `youlaitech/youlai-boot` | `sql/mysql/youlai_admin.sql` |
| FastBee | `kerwincui/FastBee` | `springboot/sql/fastbee.sql` |

---

## 附录 C：涉及的审查文件清单

**Flyway 迁移脚本**：
- `backend/src/main/resources/db/migration/V1__baseline.sql`
- `backend/src/main/resources/db/migration/V2__seed_test_data.sql`
- `backend/src/main/resources/db/migration/V3__operation_log_check_types.sql`

**Entity 类**：7 个（User/Role/UserRole/Device/DeviceData/Alarm/OperationLog）

**Mapper 接口**：7 个 + 2 个 XML（DeviceMapper.xml / DeviceDataMapper.xml）

**配置文件**：`application.yml` / `application-dev.yml` / `application-test.yml` / `application-prod.yml` / `compose.yml` / `nginx.conf`

**前端文件**：`main.js` / `vite.config.js` / `package.json` / `router/index.js` / 6 个 `.vue` 页面

---

> **审查日期**：2026-08-21 ~ 2026-08-22
>
> **维护者**：AI 助手 + hula0710
>
> **基于**：ThingsBoard (master) / JetLinks Community (v2.11) / youlai-boot (v2.21.1) / FastBee (master) 源码分析
>
> **下一步**：执行 Day 0 数据库止血修复 → Day 1 RBAC 最小可用 → Day 2 前端修复 → 进入 Phase 4
