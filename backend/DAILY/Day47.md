# Day 47 — Spring Cache 注解集成（@Cacheable/@CacheEvict）

> 日期：2026-08-04 | 阶段：Phase 3（第 7 周 Redis）

## 今日目标

- 从“手写 CacheService 调用”升级为“注解声明式缓存”
- 用 `@Cacheable` / `@CacheEvict` 改造设备数据查询链路
- 配置 RedisCacheManager（TTL、Key 前缀、JSON 序列化）
- 补充 Spring Cache 行为单元测试，恢复并保持 80/80 全绿

## 背景问题

Day 44-46 的缓存代码需要在每个 Service 手动调用 `getOrFetch/put/evict`：

- 样板代码重复，业务逻辑被缓存逻辑淹没
- 缓存规则散落在各方法内部，难统一治理

Spring Cache 用注解把缓存规则“声明”在方法上，由 AOP 代理统一执行，
业务方法保持纯净。这是 Day 48 全项目重构的基础。

## 产出

- [x] `CacheConfig.java` — `@EnableCaching` + RedisCacheManager（30 分钟 TTL、`cache:` 前缀）
- [x] `DeviceDataService.getStats()` — `@Cacheable` 聚合统计缓存
- [x] `DeviceDataService.listByTimeRange()` — `@Cacheable` 时间范围查询缓存
- [x] `DeviceDataService.report()` — `@CacheEvict` 上报后清空统计/范围缓存
- [x] `SpringCacheDemo.java` — 进程内演示 MISS/HIT/EVICT
- [x] `DeviceDataServiceCacheTest` — 命中、隔离、失效 4 个用例

## 核心知识点

| 注解 | 作用 | 本日落点 |
------|------|------|
| `@EnableCaching` | 开启注解缓存能力 | `CacheConfig` |
| `@Cacheable` | 命中返回缓存，未命中执行并回写 | `getStats` / `listByTimeRange` |
| `@CacheEvict` | 删除缓存，避免脏数据 | `report`（allEntries=true） |

要点：

1. **Key 用 SpEL 表达式**：`#deviceId + ':' + #dataType + ':' + #startTime + ':' + #endTime`，同一查询参数复用缓存。
2. **Cache Name 即业务域**：`device-data:stats`、`device-data:range`，后续可按域治理 TTL。
3. **Value 序列化用 `GenericJackson2JsonRedisSerializer`**：不沿用 RedisConfig 的 LaissezFaire 配置（安全备忘见审计报告）。
4. **测试隔离**：test Profile 提供 `ConcurrentMapCacheManager`，单元测试不依赖 Redis 实例。

## 工业场景对标

- 设备趋势聚合（avg/min/max/count）：报表页高频刷新，相同时间窗直接命中缓存
- 历史曲线查询：时间段查询结果 30 分钟内复用
- 新数据上报：立即失效统计与范围缓存，保证曲线/报表不过期脏读

## 关键代码

- [CacheConfig.java](../src/main/java/dev/reboot/config/CacheConfig.java)
- [DeviceDataService.java](../src/main/java/dev/reboot/service/DeviceDataService.java)
- [SpringCacheDemo.java](../src/main/java/code/day47/SpringCacheDemo.java)

## 验证命令

```bash
docker compose up -d mysql redis
cd backend && ./mvnw test
# Tests run: 80, Failures: 0, Errors: 0, Skipped: 0
```

## 安全备忘

`RedisConfig` 的 `LaissezFaireSubTypeValidator` 仅限学习阶段；
生产必须使用类型白名单或 `GenericJackson2JsonRedisSerializer`。
已归档至 `docs/reports/Code-Security-Scan-2026-08-04.md` §3。

## 明日

Day 48 — Redis 实战整合：重构项目中所有可缓存的地方
