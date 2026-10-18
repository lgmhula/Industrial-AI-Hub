# Decision 0038: 活动告警聚合

| 属性 | 值 |
|------|-----|
| **状态** | 已落地 |
| **决策日期** | 2026-10-18 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 99 技术债 / Flyway V16 / ADR 0011 / ADR 0019 |

## 1. 背景

Day 99 真机联调时，DHT22 每 2 秒上报一次遥测。设备持续超温或超湿时，规则引擎会为每条遥测创建一条
新告警，短时间内形成“报警风暴”：同一设备、同一规则、同一活动周期的重复记录淹没有效信息，也放大
列表、统计和 AI 摘要的读取成本。

原表结构无法阻止并发重复写入。只在 Service 层先查后写存在 race condition，多实例或连续消费时仍可能
产生多条活动告警。

## 2. 决策

1. `alarm` 增加 `repeat_count` 与 `last_triggered_at`，分别记录活动周期内的重复触发次数和最近触发时间。
2. 增加 MySQL generated column `active_alarm_key`：
   `status IN (0,1)` 时为 `device_id:alarm_type`，解决后为 `NULL`。
3. 为该生成列建立唯一索引 `uk_alarm_active_device_type`。
4. `AlarmService.createAlarm()` 使用 `INSERT ... ON DUPLICATE KEY UPDATE` 原子 upsert：
   - 新活动周期插入新记录；
   - 同设备同类型未解决告警累加 `repeat_count`；
   - 刷新 `last_triggered_at`、级别、描述和 `updated_at`。
5. Flyway V16 在创建唯一索引前先归并存量活动重复项：保留最早 ID，其余标记为已解决并保留审计历史。
6. API DTO 暴露 `repeatCount` 与 `lastTriggeredAt`，前端告警列表显示重复次数与最近触发时间。

## 3. 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| Service 层先查再更新 | 并发下仍可能双写；无法由数据库兜底 |
| 只做前端按设备/类型折叠 | 数据库继续膨胀，统计与 AI 输入仍被噪声污染 |
| 定时清理重复告警 | 延迟高且丢失审计轨迹，不能解决活动周期语义 |
| Redis SETNX 去重 | Redis 异常时失效；告警最终事实应落在 MySQL，唯一索引更直接 |

## 4. 后果与风险

- 活动告警语义从“每次触发一条”变为“每个设备/类型一个活动周期”，查询与通知消费方需要基于
  `repeat_count` 判断是否重复触发。
- 解决后唯一键变 `NULL`，再次触发会创建新周期；历史记录不被覆盖。
- 多实例并发由数据库唯一索引保证，不依赖应用时钟或本地锁。
- V16 会修改存量活动重复项状态；迁移保留原始行，但将非保留项标记为已解决。

## 5. 验证证据

- `MySqlMigrationV16IT`：真实 MySQL 验证存量归并、唯一索引、upsert 累加与 resolved 后新周期。
- `AlarmServiceTest`：覆盖首次创建、重复合并、响应字段映射。
- `MySqlSeedIsolationIT` 与 H2 schema 同步验证迁移/测试基线。
- 全量后端回归已通过；最终数字以本轮收口复跑结果为准。
