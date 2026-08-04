# 代码安全与关键配置扫描报告

> 日期：2026-08-04 | 范围：backend 主代码 + 配置文件 | 基线：v2.1.0

---

## 1. 扫描范围与方法

- 扫描目录：`backend/src/main/java`、`backend/src/main/resources`、`.env.example`
- 扫描项：硬编码密钥/密码、TODO/FIXME、`System.out`/`printStackTrace`、
  Jackson 反序列化配置、明文密码残留、敏感文件 Git 追踪状态
- 测试基线：`./mvnw test` → **76/76 通过**

## 2. 发现清单

| # | 严重度 | 位置 | 问题 | 状态 |
|:--:|:--:|------|------|------|
| 1 | P0（生产） | `config/RedisConfig.java` | `LaissezFaireSubTypeValidator` + `activateDefaultTyping(NON_FINAL)`，Jackson 多态反序列化存在 RCE 风险 | 学习阶段接受，生产必须整改，见 §3 |
| 2 | P2 | `config/JwtConfig.java` | 未设置 `JWT_SECRET` 时使用源码内开发回退密钥 | prod profile 已 fail-fast；dev 回退密钥仅本地可用 |
| 3 | P2 | `resources/code/day19/mybatis-config.xml`、`code/day20/mybatis-config.xml` | 学习代码配置内含明文密码 `1zxcvbnm` | 学习目录，不进生产路径 |
| 4 | 通过 | `.env` | 已被 `.gitignore` 忽略，未进入 Git 追踪 | 通过 |
| 5 | 通过 | `application.yml` | `MYSQL_PASSWORD`、`REDIS_PASSWORD`、`JWT_SECRET` 均无真实默认值 | 通过 |
| 6 | 通过 | `application-test.yml` | 测试 Profile 使用独立测试密钥，不污染生产 | 通过 |
| 7 | 通过 | `dev/reboot` 主代码 | 无 `TODO`/`FIXME` 残留；`System.out` 仅存在于 `code/day*` 学习 Demo | 通过 |

## 3. ⚠️ 安全备忘：RedisConfig Jackson 反序列化 RCE 风险

```text
RedisConfig 当前使用 LaissezFaireSubTypeValidator + activateDefaultTyping(NON_FINAL)
—— 存在 Jackson 反序列化 RCE 风险，仅限学习阶段使用。
生产环境必须改为：类型白名单 或 GenericJackson2JsonRedisSerializer。
```

约束：

- 不得将当前 `objectRedisTemplate` 的序列化策略直接用于生产。
- Day 47 的 `RedisCacheManager` 已采用 `GenericJackson2JsonRedisSerializer` 作为生产可用方向；
  Day 48 整合时统一收敛序列化策略。

## 4. 验证证据

```bash
cd backend && ./mvnw test
# Tests run: 76, Failures: 0, Errors: 0, Skipped: 0
```

## 5. 后续跟踪

- [ ] Phase 3-B：Redis 序列化策略收敛（移除 LaissezFaire）
- [ ] 学习目录明文密码随课程归档处理（不影响运行时）
- [ ] 生产部署前复核 `JWT_SECRET` 注入与密钥轮换
