# 项目全面审计报告

> 日期：2026-08-04 | 审计角色：Infrastructure Architect + Code Reviewer  
> 范围：backend 业务代码 + 配置文件 + 前端 + 基础设施 + 文档体系  
> 基线：v2.1.0 (ec9a158) → HEAD (43247fa) + 未提交工作区  
> 模式：**只读审计，未修改任何文件**

---

## 0. 执行摘要

| 维度 | 评分 | 趋势 |
|:----:|:----:|:----:|
| 架构分层 | A | 稳定 |
| 代码重复度 | C | ⚠️ 恶化中 |
| 耦合度 | B- | ⚠️ 缓慢恶化 |
| 配置清晰度 | B+ | 改善中 |
| 文档完整度 | A- | 稳定 |
| 测试覆盖 | A | 改善 |
| 安全态势 | B | 已知风险待修 |

**总评：项目处于"可控技术债"阶段，尚未形成屎山，但 Day 44-47 引入的缓存层已产生 3 处重复模式，如不收敛将在 Day 50+ 爆发。**

---

## 1. 架构质量（正面发现）

### 1.1 分层架构清晰 ✅

```
Controller (6) → Service (8) → Mapper (7) → MySQL
     ↕                ↕
  Security(3)    CacheService / @Cacheable → Redis
  AOP(1)
```

- **构造器注入 100%**：全部 Service/Controller/Config 使用构造器注入，零 `@Autowired` 字段注入
- **异常统一处理**：`BusinessException` + `GlobalExceptionHandler` 覆盖 4 类异常
- **AOP 操作日志**：`@OperationLog` 注解 + `OperationLogAspect` 切面，无侵入
- **VO/DTO 隔离**：Controller 返回 VO（脱敏），Service 接收 DTO（校验）

### 1.2 安全链完整 ✅

```
JwtAuthFilter (Filter, order=1)
    → RateLimitInterceptor (order=0, Guava RateLimiter)
    → AuthInterceptor (order=1, @RequireRole RBAC)
```

- JWT 从 static 重构为 instance Bean（Phase 3-A T4）
- Profile-aware 密钥策略：prod fail-fast / dev fallback / test 固定密钥
- BCrypt 密码加密，无明文存储

### 1.3 测试覆盖 ✅

```
Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
```

| 测试类 | 测试数 | 覆盖范围 |
|--------|:------:|----------|
| AuthServiceTest | 6 | 登录/注册/异常 |
| UserServiceTest | 19 | CRUD/缓存/密码/状态 |
| DeviceServiceTest | 16 | CRUD/缓存/搜索 |
| AlarmServiceTest | 9 | 分页/确认/解决 |
| AlarmDetectorTest | 12 | 规则匹配/边界 |
| DeviceDataServiceTest | 7 | 上报/查询/统计 |
| DeviceDataServiceCacheTest | 4 | @Cacheable 命中/失效 |
| OperationLogServiceTest | 6 | 日志记录 |
| ApplicationContextLoadTest | 1 | 上下文冒烟 |

### 1.4 文档体系完整 ✅

- **13 份 ADR**（0001-0013）覆盖所有关键技术决策
- **6 份架构文档**（API-Reference, Application-Architecture, Database-ER, Infrastructure-Baseline, System-Architecture, README）
- **AGENTS.md** 作为 AI 入口文件，140 行
- **DAILY_ROADMAP.md** 694 行，112 天学习路线
- **9 份审计/验收报告**归档在 `docs/reports/`

---

## 2. 代码重复度分析（屎山预警）

### 2.1 🔴 PageInfo 转换代码重复 5 份

**严重度：HIGH — 如不抽取将在每个新 Service 中复制**

以下 5 处代码几乎逐行相同（~10 行/处 = ~50 行重复）：

| 位置 | 方法 |
|------|------|
| `AlarmService.listAllPaged()` | L31-43 |
| `AlarmService.listByDevicePaged()` | L47-59 |
| `AlarmService.listByStatusPaged()` | L63-75 |
| `UserService.listPage()` | L53-65 |
| `DeviceService.searchDevices()` | L166-181 |

重复模式：
```java
PageHelper.startPage(page, size);
List<Entity> records = mapper.findAll();
PageInfo<Entity> raw = new PageInfo<>(records);
List<VO> voList = records.stream().map(VO::from).toList();
PageInfo<VO> result = new PageInfo<>();
result.setList(voList);
result.setTotal(raw.getTotal());
result.setPageNum(raw.getPageNum());
result.setPageSize(raw.getPageSize());
result.setPages(raw.getPages());
result.setSize(voList.size());
return result;
```

**建议**：抽取 `PageUtil.toPageInfo(List<T>, Function<T, R>)` 工具方法。

### 2.2 🔴 缓存读写助手重复 2 份

**严重度：HIGH — 每增加一个缓存实体就会再复制一份**

`DeviceService` 和 `UserService` 各有一对几乎相同的方法：

| DeviceService | UserService | 差异 |
|---------------|-------------|------|
| `getCachedDeviceVO(key)` L68-78 | `getCachedUserVO(key)` L92-102 | 仅类型不同 |
| `cacheDeviceVO(key, vo)` L80-87 | `cacheUserVO(key, vo)` L104-111 | 仅类型不同 |

**建议**：抽取 `CacheHelper.getCached(key, ttl, targetClass, objectMapper, cacheService, supplier)` 泛型方法。

### 2.3 🟡 硬编码值散布

| 硬编码值 | 出现次数 | 位置 |
|----------|:--------:|------|
| `"device:id:"` | 3 | DeviceService L53, L129, L144 |
| `"user:id:"` | 3 | UserService L76, L131, L163 |
| `"lock:"` | 1 | CacheService L80 |
| `Duration.ofMinutes(30)` | 6 | CacheService L37, DeviceService L70/83, UserService L94/107, CacheConfig L43 |

**注意**：`CacheService` 和 `CacheConfig` 各自独立定义了 `DEFAULT_TTL = Duration.ofMinutes(30)`，值相同但无统一来源。

**建议**：定义 `CacheKeys` 常量类 + `CacheTTL` 配置属性。

### 2.4 🟡 ObjectMapper 多实例

3 处 `new ObjectMapper()` 独立实例：

| 位置 | 用途 |
|------|------|
| `RedisConfig` L46 | RedisTemplate JSON 序列化 |
| `AuthInterceptor` L35 | 401/403 JSON 响应 |
| `RateLimitInterceptor` L33 | 429 JSON 响应 |

Spring Boot 自动配置了一个 `ObjectMapper` Bean，但这三处都没用它。

**建议**：注入 Spring 容器中的 `ObjectMapper` Bean，或创建统一的 `JsonConfig`。

---

## 3. 耦合度分析

### 3.1 🔴 双缓存机制并存，无统一策略

项目中存在**两套完全独立的缓存机制**：

| 机制 | 使用方 | 底层 | 序列化 | TTL |
|------|--------|------|--------|-----|
| `CacheService`（手动 Cache-Aside） | DeviceService, UserService | `StringRedisTemplate` | 手动 `ObjectMapper.writeValueAsString` | 硬编码 30min + jitter |
| `@Cacheable`（Spring Cache 注解） | DeviceDataService | `RedisCacheManager` (CacheConfig) | `GenericJackson2JsonRedisSerializer` | CacheConfig 30min |

**问题**：
- 两套缓存不可互通：DeviceService 的手动缓存不能通过 `@CacheEvict` 清除
- 序列化策略不同：CacheService 用 String JSON，CacheConfig 用 Jackson 类型感知 JSON
- TTL 策略不同：CacheService 有 jitter 防雪崩，CacheConfig 没有
- 新开发者不知道该用哪套

**建议**：统一为 Spring Cache 注解方式，将 CacheService 降级为底层工具或废弃。

### 3.2 🟡 objectRedisTemplate 是死代码

`RedisConfig` L36-57 定义了 `RedisTemplate<String, Object>` Bean，使用 `LaissezFaireSubTypeValidator` + `activateDefaultTyping(NON_FINAL)`。

**扫描结果**：全项目零注入（`grep -rn "objectRedisTemplate" src/main/java/dev/reboot/` → 0 匹配）。

- `CacheService` 用的是 `StringRedisTemplate`
- `CacheConfig` 的 `RedisCacheManager` 自己创建 `GenericJackson2JsonRedisSerializer`
- 没有任何代码注入 `RedisTemplate<String, Object>`

**结论**：这个 Bean 是死代码，且它的 `LaissezFaireSubTypeValidator` 配置是唯一的 RCE 风险点。删除它即可消除安全扫描报告中的 P0 项。

### 3.3 🟡 CacheService @Profile("!test") 造成测试裂缝

```
CacheService @Profile("!test")  →  test profile 下无 Bean
    ↓
DeviceService 构造器注入 CacheService  →  依赖缺失
UserService 构造器注入 CacheService  →  依赖缺失
    ↓
ApplicationContextLoadTest 必须 @MockBean CacheService 才能启动
```

**当前 workaround**：`ApplicationContextLoadTest` L40-41 使用 `@MockBean private CacheService cacheService`

**问题**：
1. `@MockBean` 在 Spring Boot 3.4+ 已废弃（应改用 `@MockitoBean`）
2. 每个需要 Spring 上下文的测试都要记得加这个 MockBean
3. 如果忘记加，错误信息不直观（UnsatisfiedDependencyException）

**建议**：在 test profile 下创建 `NoOpCacheService` 或将 CacheService 改为 `@ConditionalOnProperty`。

### 3.4 🟡 学习代码与业务代码同模块编译

```
src/main/java/
├── code/          ← 62 个学习文件（Day 01-47）
│   ├── day01/
│   └── ...day47/
└── dev/reboot/    ← 60 个业务文件
```

- 学习代码和业务代码在同一个 Maven 模块，一起编译、一起打包
- `src/main/resources/code/day18/mybatis-config.xml` 含明文密码 `1zxcvbnm`
- `.dockerignore` 已排除 `**/code/`，但本地 `mvn package` 仍会打包进 JAR
- 学习代码的 `main()` 方法可能被 Spring Boot 的 `spring-boot-maven-plugin` 误认为主类

**建议**：将 `code/` 移到独立模块或 `src/test/java/code/`。

---

## 4. 配置质量审计

### 4.1 ✅ 配置文件层次清晰

```
application.yml          ← 公共（ datasource, redis, mybatis, jwt, actuator ）
├── application-dev.yml  ← dev（ DEBUG 日志, SQL 打印, Swagger 开启 ）
├── application-prod.yml ← prod（ WARN 日志, Swagger 关闭, Tomcat 调优 ）
└── application-test.yml ← test（ 排除 Redis 自动配置, 固定 JWT 密钥 ）
```

环境变量注入完整：`MYSQL_HOST`, `MYSQL_PASSWORD`, `REDIS_HOST`, `REDIS_PASSWORD`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE`

### 4.2 ⚠️ application-test.yml 位置错误

**现状**：`src/main/resources/application-test.yml`

**问题**：生产 JAR 中包含测试配置文件。虽然 `prod` profile 不会激活它，但：
- 测试密钥 `test-secret-for-context-load-at-least-256-bits` 随 JAR 分发
- 违反"测试代码不进生产"原则

**标准做法**：移到 `src/test/resources/application-test.yml`

### 4.3 ⚠️ RedisConfig RCE 风险（已知未修）

```java
// RedisConfig.java L47-48
mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
```

**状态**：安全扫描报告已记录为 P0，但因 `objectRedisTemplate` 是死代码，实际风险为零。删除该 Bean 即可消除。

### 4.4 ✅ compose.yml 质量良好

- 15 个服务（含网络、数据卷）
- profiles 分层：默认 4 服务（core），`full` profile 9 服务
- healthcheck 覆盖全部服务
- MinIO 版本锁定 `RELEASE.2025-09-07`
- `.dockerignore` 排除学习代码

### 4.5 ✅ 前端配置清晰

- Vue 3 + Element Plus + ECharts + Axios
- 路由懒加载（登录页不含 ECharts）
- API 层统一封装（interceptor 处理 401 跳转 + 业务状态码校验）
- 6 个页面（Login, Dashboard, DeviceList, DeviceDetail, AlarmList, OperationLogList）

---

## 5. 工作区状态审计

### 5.1 ⚠️ 未提交变更（9 文件）

```
Modified:
  AGENTS.md
  backend/DAILY/Day44.md, Day45.md, Day46.md
  backend/DAILY_ROADMAP.md
  backend/src/main/java/dev/reboot/service/DeviceDataService.java
  backend/src/test/java/dev/reboot/ApplicationContextLoadTest.java

Untracked:
  backend/DAILY/Day47.md
  backend/src/main/java/code/day47/SpringCacheDemo.java
  backend/src/main/java/dev/reboot/config/CacheConfig.java
  backend/src/test/java/dev/reboot/service/DeviceDataServiceCacheTest.java
  docs/reports/Code-Security-Scan-2026-08-04.md
```

**风险**：Day 47 的 `CacheConfig` 和 `ApplicationContextLoadTest` 修复（@MockBean CacheService）是测试通过的关键。如果只提交部分文件，测试会断裂。

**建议**：将 Day 47 + 安全扫描报告作为一次完整提交。

### 5.2 ✅ v2.1.0 Tag 完好

```
v2.1.0 → ec9a158f0f87d8b38bd4cbd3686196581356172e
```

Tag 未被移动，冻结基线安全。

---

## 6. 高风险项清单

| # | 严重度 | 问题 | 位置 | 修复建议 | 影响范围 |
|:--:|:------:|------|------|----------|----------|
| R1 | **HIGH** | PageInfo 转换重复 5 份 | AlarmService/UserService/DeviceService | 抽取 `PageUtil.toPageInfo()` | 每新增分页接口都会复制 |
| R2 | **HIGH** | 缓存助手重复 2 份 | DeviceService/UserService | 抽取 `CacheHelper` 泛型方法 | 每新增缓存实体都会复制 |
| R3 | **HIGH** | 双缓存机制并存无统一策略 | CacheService vs @Cacheable | 统一为 Spring Cache 注解 | 缓存不可互通，TTL/序列化不一致 |
| R4 | **MODERATE** | objectRedisTemplate 死代码 + RCE | RedisConfig L36-57 | 删除该 Bean | 消除 P0 安全风险 |
| R5 | **MODERATE** | application-test.yml 在 main/resources | src/main/resources/ | 移到 src/test/resources/ | 测试密钥随 JAR 分发 |
| R6 | **MODERATE** | 学习代码与业务代码同模块 | src/main/java/code/ | 移到独立模块或 test 目录 | JAR 膨胀 + 潜在主类冲突 |
| R7 | **MODERATE** | 硬编码缓存 Key 和 TTL | 6 处字符串 + 6 处 Duration | 定义 CacheKeys 常量类 | 重构时需全文搜索替换 |
| R8 | **LOW** | @MockBean 已废弃 | ApplicationContextLoadTest | 改用 @MockitoBean | 编译警告 |
| R9 | **LOW** | ObjectMapper 多实例 | 3 处 new ObjectMapper() | 注入容器内 Bean | 配置不一致风险 |
| R10 | **LOW** | 9 文件未提交 | 工作区 | 提交 Day 47 + 安全扫描 | 代码丢失风险 |

---

## 7. 任务优先级建议（按影响/成本排序）

### Tier 1：快速修复（< 1h，消除安全风险 + 死代码）

1. **删除 objectRedisTemplate Bean**（R4）— 零依赖，消除 RCE 风险
2. **提交工作区变更**（R10）— Day 47 + 安全扫描报告归档
3. **移动 application-test.yml**（R5）— 移到 src/test/resources/

### Tier 2：代码重构（2-4h，消除重复模式）

4. **抽取 PageUtil**（R1）— 消除 5 份重复
5. **抽取 CacheHelper**（R2）— 消除 2 份重复
6. **定义 CacheKeys 常量类**（R7）— 集中管理缓存 Key 和 TTL

### Tier 3：架构收敛（4-8h，需设计决策）

7. **统一缓存策略**（R3）— 将 CacheService 的功能收敛到 Spring Cache 注解
8. **学习代码分离**（R6）— 移到独立 Maven 模块
9. **@MockBean → @MockitoBean**（R8）— 消除废弃警告

---

## 8. 整体评价

### 不是屎山，但正在积累技术债

项目的**骨架质量很高**——分层清晰、注入规范、异常统一、文档完整、测试覆盖好。这在学习项目中属于前 5% 水平。

但 Day 44-47 引入缓存层时，没有做"先设计后编码"，而是**每个 Service 各自实现了一套缓存读写**，导致：
- 5 份 PageInfo 重复
- 2 份缓存助手重复
- 2 套缓存机制并存
- 硬编码值散布

这是典型的**"第一次写时不抽，第二次复制时忍，第三次就变成屎山"**路径。目前处于第二次阶段，抽取消耗 2-4h；如果等到 Day 50+（RabbitMQ 消费者也需要缓存），成本将翻倍。

### 建议的执行节奏

```
Day 47（当前）→ 提交工作区
Day 48        → Tier 1 快速修复（1h）
Day 49        → Tier 2 重构（2-4h）
Day 50+       → 继续 DAILY_ROADMAP，此时缓存层已干净
```

**不要在引入新缓存使用方之前跳过 Tier 2**——否则 R1/R2 的重复模式会扩散到第 3、4 份。
