# Day 48 — Redis 实战整合：重构项目中所有可缓存的地方

> 日期：2026-08-04 | 阶段：Phase 3（第 7 周 Redis）

## 今日目标

- 将 DeviceService、UserService 的手写 `CacheService` 调用迁移到 Spring Cache 注解
- 安全清理 `RedisConfig` 中的 `LaissezFaireSubTypeValidator`
- 补充注解缓存行为测试，保持 80+ 全绿

## 背景

Day 44-46 引入的 `CacheService` 提供了 Cache-Aside、互斥锁、随机 TTL 等模式，
但需要在每个 Service 手动调用 `getOrFetch/put/evict`，样板代码重复。
Day 47 在 `DeviceDataService` 上验证了 `@Cacheable/@CacheEvict` 注解方案，
Day 48 将其推广到项目其余可缓存位置。

## 产出

### 1. CacheConfig 扩展
- 新增 `CACHE_DEVICE_DETAIL = "device:detail"` — 设备详情缓存
- 新增 `CACHE_USER_DETAIL = "user:detail"` — 用户详情缓存
- `simpleCacheManager` (test profile) 同步注册新缓存名

### 2. DeviceService 迁移
- `getById()`: 手写 `getCachedDeviceVO/cacheDeviceVO` → `@Cacheable(key = "#id")`
- `update()`: 新增 `@CacheEvict(key = "#id")` 
- `delete()`: 新增 `@CacheEvict(key = "#id")`
- 移除 `CacheService` / `ObjectMapper` 依赖，构造器简化为 `(DeviceMapper)`

### 3. UserService 迁移
- `getById()`: 手写 `getCachedUserVO/cacheUserVO` → `@Cacheable(key = "#id")`
- `update()`: 新增 `@CacheEvict(key = "#id")`
- `toggleStatus()`: 新增 `@CacheEvict(key = "#id")` — 状态变更也应失效缓存
- `delete()`: 新增 `@CacheEvict(key = "#id")`
- `changePassword()`: 不需要缓存失效 — UserVO 不含密码字段
- 移除 `CacheService` / `ObjectMapper` 依赖

### 4. RedisConfig 安全清理
- `objectRedisTemplate` Bean 标记 `@Deprecated`
- Javadoc 明确引用安全风险：`LaissezFaireSubTypeValidator` 存在反序列化漏洞
- 指引生产代码使用 `CacheConfig` + `GenericJackson2JsonRedisSerializer`

### 5. 测试
- `DeviceServiceTest` / `UserServiceTest`: 移除 `@Mock CacheService`，精简为纯业务逻辑测试
- 新增 `DeviceServiceCacheTest` (4 用例): 缓存命中 / ID 隔离 / update 失效 / delete 失效
- 新增 `UserServiceCacheTest` (5 用例): 缓存命中 / ID 隔离 / update 失效 / toggleStatus 失效 / delete 失效

## 缓存覆盖全景

| 缓存域 | 缓存名 | 注解 | 失效触发 |
|--------|--------|------|----------|
| 设备数据聚合 | `device-data:stats` | `@Cacheable` | `report()` → `@CacheEvict` |
| 设备数据范围 | `device-data:range` | `@Cacheable` | `report()` → `@CacheEvict` |
| 设备详情 | `device:detail` | `@Cacheable` | `update()`/`delete()` → `@CacheEvict` |
| 用户详情 | `user:detail` | `@Cacheable` | `update()`/`toggleStatus()`/`delete()` → `@CacheEvict` |

## 未缓存说明

- **AlarmService**: 所有读操作均为动态分页（page/size 组合无限），缓存命中率极低，不缓存
- **AuthService.login()**: 认证逻辑涉密，缓存 token 是反模式
- **CacheService 类**: 保留作为学习参考（防穿透/击穿/雪崩实现），不再被 production Service 依赖

## 验证命令

```bash
cd backend && ./mvnw test
# Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
```

## 明日

Day 49 — 周复盘 + Redis 笔记整理
