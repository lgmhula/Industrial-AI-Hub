# Day 23 审计修复 — 2026-07-24

## 审计来源
33 项问题审计报告（P0×4 / P1×7 / P2×11 / P3×11）。

## 本次修复（12 项）

### P0 安全（4 项）
- [x] **#1 JWT 密钥硬编码** → 改为从 `JWT_SECRET` 环境变量读取，源码无密钥
- [x] **#2 密码明文默认值** → `application.yml` 移除 `:1zxcvbnm` 默认值，仅读 `MYSQL_PASSWORD` 环境变量
- [x] **#5 注册并发竞态** → `AuthService.register()` 捕获 `DuplicateKeyException`，返回 null 而非 500
- [x] **#28 .gitignore 缺中间件数据** → 新增 nacos/rabbitmq/minio/elasticsearch 数据目录

### P1 数据安全 + 功能阻塞（5 项）
- [x] **#3 注册返回 User 实体** → 新增 `RegisterResponse` DTO，password 字段绝不出现在响应中
- [x] **#6 注册无默认角色** → 注册时自动分配 VIEWER 角色（`user_role` 插入）
- [x] **#13 UserRoleMapper 缺失** → 新建 `UserRoleMapper`，为 Day 24 RBAC 提供基础
- [x] **#27 UserService 返回密码** → 新增 `UserVO`（含 `from(User)` 工厂方法），`listAll()`/`getById()` 返回 UserVO
- [x] **#31 AGENTS.md 未同步** → §3 更新至 Day 23 完成 + Day 24 待做

### P2 隐患（3 项）
- [x] **#8/#17 `user` 保留字** → `UserMapper` 五条 SQL 全部加反引号 `` `user` ``
- [x] **#11 登录失败原因不区分** → `AuthService.login()` 三种失败情况分别打 WARN 日志
- [x] **#16 mapper-locations 路径** → `classpath:code/**/*Mapper.xml` → `classpath*:code/**/*Mapper.xml, classpath*:mapper/**/*Mapper.xml`

## 技术栈决策
| 决策 | 结论 | 理由 |
|------|------|------|
| Lombok | **暂不引入** | 7 个 entity，样板代码可控。Lombok 引入 annotation processor + IDE 插件依赖，不划算。后续 entity 超过 15 个时再评估。 |
| DOUBLE→DECIMAL | **Day 29 一起** | 工业精度重要，但数据迁移需统一窗口 |
| enum 状态常量 | **Day 24 开始** | RBAC 角色天然需要枚举，从下一天开始 |
| 全局异常处理 | **Day 26** | 路线图安排，不改排期 |
| 逻辑删除 | **Day 29** | 同上 |

## 编译验证
- `mvn clean compile`: 86 files, **BUILD SUCCESS**

## 新增文件
```
dev.reboot.dto.RegisterResponse    # 注册响应（无 password）
dev.reboot.dto.UserVO              # 用户视图（无 password）
dev.reboot.mapper.UserRoleMapper   # RBAC 权限基础
```

## 修改文件
| 文件 | 变更 |
|------|------|
| application.yml | 移除密码默认值、新增 JWT 配置段、修正 mapper-locations |
| .gitignore | 补全 nacos/rabbitmq/minio/elasticsearch 数据目录 |
| .env.example | 新增 JWT_SECRET + JWT_EXPIRATION_MS |
| JwtUtils.java | 密钥从 `JWT_SECRET` 环境变量读取 |
| AuthService.java | RegisterResponse + DuplicateKeyException + slf4j 日志 |
| AuthController.java | 返回 RegisterResponse |
| UserService.java | listAll/getById 返回 UserVO |
| UserMapper.java | 表名全部加反引号 |
| AGENTS.md | §3 同步至 Day 23 完成 |

## 未修复项（归入后续 Day）
| 审计# | 归入 |
|:---:|------|
| 6 (默认角色已修) | — |
| 9 | Day 24 JWT Filter |
| 14, 15 | Day 26 全局异常处理 |
| 18, 19 | Day 29 逻辑删除 + 精度修正 |
| 21 | Day 24+ 枚举替换 |
| 22 | 用户手动创建 admin 账户 |
| 25 | 后续评估 Lombok |
| 29, 30 | P3 运维改进 |
