# Industrial AI Hub — 数据库设计对比报告

> **对比对象**：Industrial AI Hub（本项目） vs ThingsBoard vs JetLinks vs youlai-boot
>
> **审查日期**：2026-08-21

---

## 一、总体架构对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 数据库 | MySQL 8.4 | PostgreSQL + Cassandra/TimescaleDB | PostgreSQL + ES/TDengine | MySQL 5.7~8.x |
| 表数量 | 7 张 | 30+ 张 | 15+ 张 | 16 张 |
| 建表方式 | Flyway 迁移脚本 | SQL 迁移脚本 | EasyORM 注解自动建表 | SQL 脚本 |
| ORM | MyBatis | JDBC + 自定义 DAO | hsweb EasyORM (R2DBC) | MyBatis-Plus |
| 编程范式 | 同步 (Spring MVC) | 同步 + Actor | 全链路响应式 (Reactor) | 同步 (Spring MVC) |
| 表命名 | 无前缀 | `tb_` / `entity_` / 无前缀 | `dev_` / `s_` / `rule_` / `alarm_` | `sys_` / `gen_` |

### 核心差异

- **ThingsBoard**：混合存储（关系型 + NoSQL），实体数据用 PostgreSQL，时序数据按规模选 Cassandra 或 TimescaleDB，大表按时间声明式分区
- **JetLinks**：混合持久化（Polyglot Persistence），元数据存 PostgreSQL，时序数据按产品级 `store_policy` 可选 ES/TDengine/ClickHouse/InfluxDB/Cassandra
- **youlai-boot**：纯 MySQL，权限管理为主，无 IoT 业务
- **本项目**：纯 MySQL，单体架构，7 张表覆盖设备管理核心业务

---

## 二、主键设计对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 类型 | `BIGINT AUTO_INCREMENT` | `uuid` (PostgreSQL UUID) | `String` (雪花算法) | `bigint AUTO_INCREMENT` |
| 分布式友好 | ❌ 自增 ID 不支持分布式 | ✅ UUID 全局唯一 | ✅ 雪花 ID 全局唯一 | ❌ 自增 ID |
| 可自定义 | ❌ 不支持 | ❌ UUID 不可读 | ✅ 支持业务自定义 ID（如设备 SN） | ❌ 不支持 |
| 多态引用 | ❌ 各表 ID 独立 | ✅ `relation` 表用 `from_id`/`to_id` 通用引用任意实体 | ❌ 各表独立 | ❌ 各表独立 |

### ThingsBoard 的 UUID 设计亮点

所有实体共用同一 UUID ID 空间，`relation` 表通过 `from_id + from_type + to_id + to_type` 实现通用实体关系图模型：

```sql
CREATE TABLE relation (
    from_id uuid, from_type varchar(255),
    to_id uuid, to_type varchar(255),
    relation_type_group varchar(255),
    relation_type varchar(255)
);
```

任何实体（设备、资产、用户、告警）之间都可以建立关系，无需为每种关系建独立的关联表。

### JetLinks 的雪花算法 + 可自定义 ID

```java
@GeneratedValue(generator = Generators.SNOW_FLAKE)
@Pattern(regexp = "^[0-9a-zA-Z_\\-]+$")
public String getId() { return super.getId(); }
```

支持用户在创建设备时直接使用设备 SN 作为 ID，无需额外映射表——这对 IoT 场景非常实用。

---

## 三、多租户设计对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 支持 | ❌ 无 | ✅ 完整 | ✅ 完整 | ❌ 无 |
| 实现方式 | — | `tenant_id` 列 + 唯一约束前缀 | 维度-资产模型（AOP 注入） | — |
| 隔离粒度 | — | 租户 → 客户 → 用户三级 | 租户 → 机构 → 用户三级 | — |
| 数据库侵入 | — | 每张表都有 `tenant_id` 列 | 不侵入表结构 | — |

### ThingsBoard：tenant_id 无处不在

```sql
-- 每张实体表都携带 tenant_id
CREATE TABLE device (
    id uuid PRIMARY KEY,
    tenant_id uuid,                    -- 数据隔离字段
    customer_id uuid,                 -- 客户级二次隔离
    CONSTRAINT device_name_unq_key UNIQUE (tenant_id, name)  -- 租户内唯一
);
-- 所有索引以 tenant_id 为前导列
CREATE INDEX idx_device_customer_id ON device(tenant_id, customer_id);
```

### JetLinks：维度-资产模型（不侵入表结构）

```
维度类型：user / tenant / org
资产类型：organization / product / device / deviceGroup / deviceCategory

通过独立的资产绑定关系表实现多维度数据隔离，
AOP 拦截 Controller 方法自动注入查询条件。
```

### 本项目：零多租户

7 张表均无 `tenant_id` 字段，需大规模改造才能支持多租户。

---

## 四、设备管理表结构对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks |
|------|-------------------|-------------|----------|
| 设备模板 | ❌ 无 | ✅ `device_profile` | ✅ `dev_product`（物模型） |
| 设备实例 | `device` 表 | `device` 表 | `dev_device_instance` 表 |
| 设备凭证 | ❌ 无独立表 | ✅ `device_credentials` 独立表 | ✅ 设备配置内嵌 |
| 物模型 | ❌ 无 | JSONB `profile_data` | JSON `metadata` 字段 |
| 父子设备 | ❌ 无 | ✅ `relation` 表 | ✅ `parent_id` 自引用 |
| 设备标签 | ❌ 无 | ✅ `attribute_kv` | ✅ `dev_device_tags` 独立表 |
| OTA 固件 | ❌ 无 | ✅ `ota_package` 表 | ✅ `firmware` 表 |
| 设备分组 | ❌ 无 | ✅ `entity_group`（PE 版） | ✅ `device_group` 表 |

### ThingsBoard 的 Profile 模板模式

```
device_profile (设备模板)
  ├── profile_data: jsonb      -- 告警规则、传输配置、provision 配置
  ├── transport_type           -- MQTT/HTTP/CoAP/LwM2M/SNMP
  ├── default_rule_chain_id    -- 默认规则链
  ├── default_dashboard_id    -- 默认仪表盘
  └── firmware_id             -- 默认固件

device (设备实例)
  ├── device_profile_id → FK device_profile(id)  -- 必须属于一个 Profile
  ├── device_data: jsonb                        -- 设备级配置覆盖
  └── customer_id                              -- 所属客户
```

### JetLinks 的物模型设计

```java
// dev_product 表
metadata: LONGVARCHAR/CLOB  -- 物模型定义（JSON 字符串）
// 包含：属性（properties）、功能（functions）、事件（events）、标签（tags）

// dev_device_instance 表
derive_metadata: LONGVARCHAR  -- 派生物模型（设备可覆盖产品物模型）
// 设备物模型 = 产品物模型 merge 设备派生物模型
```

### 本项目的设备表

```sql
CREATE TABLE device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100),
    device_code VARCHAR(50) UNIQUE,
    device_type ENUM('SENSOR','ACTUATOR','GATEWAY','CONTROLLER'),
    status TINYINT DEFAULT 1,          -- 默认"在线"（不合理）
    location VARCHAR(200),
    port INT,                          -- 类型过大（应为 SMALLINT UNSIGNED）
    is_deleted TINYINT DEFAULT 0
    -- ❌ 无 manufacturer/model/serial_number/firmware_version
    -- ❌ 无 device_profile_id（无模板概念）
    -- ❌ 无 parent_id（无父子设备）
    -- ❌ 无 tenant_id（无多租户）
);
```

---

## 五、告警表结构对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks |
|------|-------------------|-------------|----------|
| 告警状态 | 3 状态（0未处理/1已确认/2已解决） | 2 布尔位（acknowledged + cleared） | 枚举状态 |
| 确认人记录 | ❌ 无 | ✅ `assignee_id` | ✅ `creator_id` |
| 确认时间 | ❌ 无 `acknowledged_at` | ✅ `ack_ts` (bigint) | ✅ `create_time` |
| 解决人记录 | ❌ 无 | ✅ `assignee_id` | ✅ `resolver_id` |
| 告警评论 | ❌ 无 | ✅ `alarm_comment` 分区表 | ✅ 告警历史 |
| 告警传播 | ❌ 无 | ✅ `entity_alarm` 关联表 + `propagate` 字段 | ✅ 场景联动 |
| 告警级别 | `alarm_level` TINYINT | `severity` varchar（5 级） | `level` Integer |
| 规则关联 | ❌ 无 `rule_id` | ✅ 规则链触发 | ✅ `alarm_config` 关联场景 |
| 实际值/阈值 | ❌ 无 | ✅ `additional_info` | ✅ 规则配置中 |

### ThingsBoard 告警表

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
    acknowledged boolean,        -- 是否已确认
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

### 本项目告警表

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
    -- ❌ 无 acknowledged_at
    -- ❌ 无 acknowledged_by
    -- ❌ 无 resolved_by
    -- ❌ 无 updated_at
    -- ❌ 无 rule_id
    -- ❌ 无 actual_value / threshold_value
    -- ❌ 无 assignee_id
    -- ❌ 无 propagate 传播机制
);
```

---

## 六、时序数据存储对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks |
|------|-------------------|-------------|----------|
| 存储引擎 | MySQL（单表 `device_data`） | PostgreSQL 分区 / TimescaleDB / Cassandra | Elasticsearch / TDengine / ClickHouse / InfluxDB |
| 分区策略 | ❌ 无 | ✅ 声明式分区（按天/月/年） | ✅ ES 按月分索引 |
| TTL 清理 | ❌ 无 | ✅ 存储过程自动清理过期分区 | ✅ ES ILM / TDengine TTL |
| 最新值缓存 | ❌ 无 | ✅ `ts_kv_latest` 表 + 乐观锁 | ✅ 最新值同步到数据库 |
| Key 压缩 | ❌ 无 | ✅ `key_dictionary` 字典表（string→int） | ❌ 无 |
| 多类型值 | `value` DECIMAL + `unit` VARCHAR | ✅ `bool_v/str_v/long_v/dbl_v/json_v` 五列 | ✅ ES 多类型字段 |
| 数据保留策略 | ❌ 无 | ✅ 按 tenant 配置 TTL | ✅ 按产品级 `store_policy` |

### ThingsBoard 的时序数据三套方案

```
方案 A：纯 PostgreSQL + 声明式分区（<5K 数据点/秒）
方案 B：PostgreSQL + TimescaleDB（中规模）
方案 C：PostgreSQL + Cassandra（>5K 数据点/秒）
```

### 本项目的时序数据

```sql
CREATE TABLE device_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT,
    data_type VARCHAR(32),
    value DECIMAL(18,4),
    unit VARCHAR(16) DEFAULT NULL,   -- 允许 NULL
    recorded_at DATETIME,
    created_at DATETIME
    -- ❌ 无分区
    -- ❌ 无 TTL
    -- ❌ 无防重复唯一约束
    -- ❌ 无 Key 字典压缩
    -- ❌ value 仅 DECIMAL，不支持字符串/布尔/JSON 类型
);
```

---

## 七、权限模型对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 模型 | 固定 3 角色 + `@RequireRole` | 3 级 Authority | RBAC + 维度资产 | **用户-角色-菜单-部门** |
| 角色管理 API | ❌ 无 | ✅ | ✅ | ✅ CRUD |
| 菜单权限 | ❌ 无 | ✅（PE 版） | ✅ | ✅ `sys_menu` 三级（目录/菜单/按钮） |
| 按钮权限 | ❌ 无 | ✅（PE 版） | ✅ | ✅ `perm` 字段 `模块:资源:操作` |
| 数据权限 | ❌ 无 | ✅ tenant_id 隔离 | ✅ 维度资产 | ✅ 5 级 `data_scope` |
| 路由守卫 | ❌ 仅登录检查 | ✅ | ✅ | ✅ 动态路由 |

### youlai-boot 的四级 RBAC 模型

```
sys_user ──(sys_user_role)── sys_role ──(sys_role_menu)── sys_menu (目录C/菜单M/按钮B)
                                  │
                                  └──(sys_role_dept)── sys_dept (树形部门)

数据权限 5 级：
1-所有数据  2-部门及子部门  3-本部门  4-本人  5-自定义部门
```

### youlai-boot 菜单表设计

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
    keep_alive tinyint,
    -- 后端按菜单动态生成路由表下发前端
);
```

### 本项目的权限模型

```java
// RoleEnum.java — 3 个硬编码角色
ADMIN(1), OPERATOR(2), VIEWER(3);

// @RequireRole 注解 — 仅接口级
@RequireRole(roles = {RoleEnum.ADMIN})
@GetMapping("/users")
public ApiResponse<List<User>> list() { ... }
```

---

## 八、审计字段对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 创建时间 | ✅ `created_at` DATETIME | ✅ `created_time` bigint（毫秒时间戳） | ✅ `create_time` Long（毫秒） | ✅ `create_time` DATETIME |
| 更新时间 | ❌ 仅部分表 | ❌ 用 `version` 替代 | ✅ `modify_time` Long | ✅ `update_time` DATETIME |
| 创建人 | ❌ 无 | ❌ 无（靠 audit_log） | ✅ `creator_id` + `creator_name` | ✅ `create_by` bigint |
| 更新人 | ❌ 无 | ❌ 无 | ✅ `modifier_id` + `modifier_name` | ✅ `update_by` bigint |
| 乐观锁 | ❌ 无 | ✅ `version` bigint | ❌ 无 | ❌ 无 |
| 最后登录时间 | ❌ 无 | ❌ 无 | ❌ 无 | ❌ 无 |
| 操作日志 | ✅ `operation_log` 表 | ✅ `audit_log` 分区表 | ✅ `access_logger` | ✅ `sys_log` 表 |

### ThingsBoard 的设计哲学

- 不用 `updated_at`，用 `version` 乐观锁替代
- 不用 `created_by`，靠独立的 `audit_log` 表记录操作者
- 时间字段统一用 `bigint`（毫秒时间戳），跨时区无歧义

### JetLinks 的审计字段

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

### 本项目的审计字段

```sql
-- 全部 7 张表统一只有 created_at
created_at DATETIME DEFAULT CURRENT_TIMESTAMP
-- ❌ 无 updated_at（仅 device 表有）
-- ❌ 无 created_by
-- ❌ 无 updated_by
-- ❌ 无 version 乐观锁
```

---

## 九、软删除策略对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 策略 | 部分表软删除 | 物理删除 + 级联 | 物理删除 + 事件补偿 | 部分表软删除 |
| 一致性 | ⚠️ 不一致 | ✅ 统一 | ✅ 统一 | ⚠️ 不一致 |
| 唯一约束冲突 | 🔴 有（软删除后无法复用编码） | N/A（物理删除） | N/A（物理删除） | ⚠️ 有（同本项目） |

| 表 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|----|-------------------|-------------|----------|-------------|
| user | ✅ `is_deleted` | 物理删除 | 物理删除 | ✅ `is_deleted` |
| device | ✅ `is_deleted` | 物理删除 | 物理删除 | N/A |
| role | ❌ 物理删除 | N/A | 物理删除 | ✅ `is_deleted` |
| alarm | ❌ 物理删除 | 物理删除（状态管理） | 物理删除 | N/A |
| operation_log | ❌ 物理删除 | ✅ 分区删除 | 物理删除 | ❌ 物理删除 |
| 菜单 | N/A | N/A | N/A | ❌ 物理删除 |

---

## 十、索引设计对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 索引数量 | 12 个 | 20+ 个 | 7 个 | 8 个 |
| 冗余索引 | 🔴 2 个 | ✅ 无 | ✅ 无 | ✅ 无 |
| 复合索引 | ✅ 有 | ✅ 大量 | ✅ 有 | ✅ 有 |
| 部分索引 | ❌ 不支持（MySQL） | ✅ `WHERE cleared=false` | ❌ 不支持 | ❌ 不支持 |
| 覆盖索引 | ❌ 无 | ✅ `INCLUDE(alarm_id)` | ❌ 无 | ❌ 无 |
| 索引前导列策略 | ❌ 无规律 | ✅ `tenant_id` 始终前导 | ✅ 查询驱动 | ❌ 无规律 |

### ThingsBoard 索引设计亮点

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

### 本项目索引问题

```sql
-- 冗余索引 1：被唯一键 uk_user_role(user_id, role_id) 最左前缀覆盖
KEY idx_user_id (user_id)  -- ❌ 冗余

-- 冗余索引 2：被复合索引 idx_device_type_time(device_id, data_type, recorded_at) 最左前缀覆盖
KEY idx_device_id (device_id)  -- ❌ 冗余

-- 低选择性索引：is_deleted 只有 0/1 两个值
KEY idx_is_deleted (is_deleted)  -- ❌ 优化器不会使用
```

---

## 十一、字典/枚举管理对比

| 维度 | Industrial AI Hub | ThingsBoard | JetLinks | youlai-boot |
|------|-------------------|-------------|----------|-------------|
| 枚举管理 | CHECK 约束硬编码 | 应用层枚举 | 应用层枚举 | ✅ `sys_dict` + `sys_dict_item` 字典表 |
| 动态扩展 | ❌ 需 Flyway 迁移 | ❌ 需改代码 | ❌ 需改代码 | ✅ 运行时 CRUD |
| 前端联动 | ❌ 无 | ❌ 无 | ❌ 无 | ✅ `tag_type` 对接前端样式 |

### youlai-boot 字典表设计

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

---

## 十二、操作日志对比

| 维度 | Industrial AI Hub | ThingsBoard | youlai-boot |
|------|-------------------|-------------|-------------|
| 表名 | `operation_log` | `audit_log`（分区表） | `sys_log` |
| 分区 | ❌ 无 | ✅ 按 `created_time` 分区 | ❌ 无 |
| 请求信息 | ❌ 无 | ✅ `action_data` | ✅ `request_uri`/`request_method`/`request_params`/`response_content` |
| 客户端环境 | ❌ 无 | ❌ 无 | ✅ `ip`/`province`/`city`/`browser`/`os` |
| 性能数据 | ❌ 无 | ❌ 无 | ✅ `execution_time`（ms） |
| 操作人 | ❌ 无 `user_id` | ✅ `user_id` + `user_name` | ✅ `create_by` |
| 增长管理 | ❌ 无 | ✅ 分区 DROP | ❌ 无 |

---

## 十三、本项目独有缺陷汇总（vs 三个参考项目）

### 🔴 高危差距

| # | 本项目缺陷 | ThingsBoard 做法 | JetLinks 做法 | youlai-boot 做法 |
|---|------------|------------------|---------------|-------------------|
| 1 | **测试数据在 Flyway 迁移目录** | 迁移脚本仅 DDL | 自动建表无此问题 | SQL 脚本含数据但手动执行 |
| 2 | **alarm 无确认人/确认时间** | `assignee_id` + `ack_ts` | `creator_id` + `create_time` | N/A |
| 3 | **唯一约束与软删除冲突** | 物理删除无此问题 | 物理删除无此问题 | 有同样问题 |
| 4 | **device_data 无分区无限增长** | 声明式分区 + TTL | ES 按月分索引 + TTL | N/A |
| 5 | **operation_log 无分区** | 声明式分区 | — | MyISAM 无分区 |

### ⚠️ 中危差距

| # | 本项目缺陷 | ThingsBoard 做法 | JetLinks 做法 | youlai-boot 做法 |
|---|------------|------------------|---------------|-------------------|
| 6 | 无设备模板/Profile | `device_profile` 表 | `dev_product` 物模型表 | N/A |
| 7 | 无物模型概念 | JSONB `profile_data` | JSON `metadata` 字段 | N/A |
| 8 | 报警规则硬编码 | 规则链可视化配置 | `alarm_config` 表 + 场景联动 | N/A |
| 9 | 仅接口级权限 | tenant_id 数据隔离 | 维度资产模型 | 四级 RBAC + 5 级数据权限 |
| 10 | 无菜单/按钮权限 | ✅（PE 版） | ✅ | `sys_menu` 三级 + `perm` 字段 |
| 11 | 无审计字段（created_by 等） | audit_log 独立表 | `creator_id` + `modifier_id` | `create_by` + `update_by` |
| 12 | 无乐观锁 | `version` 列 | ❌ 无 | ❌ 无 |
| 13 | 无父子设备关系 | `relation` 通用关系表 | `parent_id` 自引用 | N/A |
| 14 | 无多租户 | `tenant_id` 列 | 维度资产模型 | N/A |
| 15 | 无字典表 | N/A | N/A | `sys_dict` + `sys_dict_item` |
| 16 | 无数据权限 | tenant_id 隔离 | 维度资产隔离 | 5 级 `data_scope` |
| 17 | role 表无 status/is_deleted | N/A | N/A | ✅ 有 `status` + `is_deleted` |
| 18 | alarm 无状态流转约束 | acknowledged + cleared 布尔位 | 枚举状态 | N/A |
| 19 | 无部门概念 | N/A | `s_organization` 树形 | `sys_dept` 树形 + `tree_path` |

### 🟢 低危差距

| # | 本项目缺陷 | 参考项目做法 |
|---|------------|-------------|
| 20 | port 用 INT | 应为 SMALLINT UNSIGNED |
| 21 | 无设备元数据字段 | ThingsBoard 有 OTA/固件管理 |
| 22 | 无 last_login_at | youlai-boot 有登录日志 |
| 23 | DATETIME vs TIMESTAMP | ThingsBoard/JetLinks 用 bigint 毫秒时间戳 |
| 24 | 无预留扩展字段 | ThingsBoard 用 JSONB `additional_info` |
| 25 | status 语义不统一 | youlai-boot 也有此问题 |

---

## 十四、值得借鉴的设计模式

### 从 ThingsBoard 借鉴

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

### 从 JetLinks 借鉴

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

### 从 youlai-boot 借鉴

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
| 9 | 代码生成器 | 加速后续模块开发 |

---

## 十五、优先改进路线图（基于对比结果）

### 第一阶段：补齐核心缺陷（1-2 周）

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 1 | alarm 表补齐 acknowledged_at/by、resolved_by | ThingsBoard | 4h |
| 2 | 修复唯一约束与软删除冲突 | — | 2h |
| 3 | device_data 表分区 + 归档策略 | ThingsBoard | 8h |
| 4 | operation_log 表分区 | ThingsBoard | 4h |
| 5 | V2 测试数据移出 Flyway 目录 | — | 1h |
| 6 | role 表补齐 status/is_deleted/updated_at | youlai-boot | 2h |
| 7 | 创建 alarm_rule 表，规则持久化 | JetLinks | 8h |

### 第二阶段：权限体系升级（2-3 周）

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 8 | 角色 CRUD API | youlai-boot | 8h |
| 9 | 用户角色分配 API | youlai-boot | 4h |
| 10 | sys_menu 菜单权限表 | youlai-boot | 8h |
| 11 | 按钮级权限指令 v-permission | youlai-boot | 4h |
| 12 | 5 级数据权限 data_scope | youlai-boot | 8h |
| 13 | 路由守卫角色检查 | youlai-boot | 2h |
| 14 | sys_dict 字典表 | youlai-boot | 4h |

### 第三阶段：设备模型升级（3-4 周）

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 15 | device_profile 设备模板表 | ThingsBoard | 8h |
| 16 | 物模型 JSON 存储 | JetLinks | 8h |
| 17 | 父子设备 parent_id | JetLinks | 4h |
| 18 | 设备标签独立表 | JetLinks | 4h |
| 19 | 设备元数据字段（manufacturer/model/serial_number） | ThingsBoard | 2h |
| 20 | version 乐观锁 | ThingsBoard | 4h |

### 第四阶段：多租户准备（4-6 周）

| # | 任务 | 参考项目 | 工作量 |
|---|------|----------|--------|
| 21 | 所有业务表添加 tenant_id | ThingsBoard | 16h |
| 22 | 唯一约束改为 (tenant_id, xxx) | ThingsBoard | 8h |
| 23 | 索引以 tenant_id 为前导列 | ThingsBoard | 8h |
| 24 | 租户管理 API + 租户配额 | ThingsBoard | 16h |

---

> 审查日期：2026-08-21 | 基于 ThingsBoard (master), JetLinks Community (v2.11), youlai-boot (v2.21.1) 源码分析
