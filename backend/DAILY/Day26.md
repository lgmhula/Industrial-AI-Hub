# Day 26 — 2026-07-25

## 今日目标
- [x] 全局异常处理：`@RestControllerAdvice` + `GlobalExceptionHandler`
- [x] 自定义业务异常：`BusinessException` + `ErrorCode` 枚举
- [x] DTO 参数校验：`@Valid` + Jakarta Validation 注解
- [x] Controller 层去除手动校验，改为声明式 `@Valid`
- [x] `application.yml` `MYSQL_PASSWORD` 默认值修复
- [x] `init.sql` `ON DUPLICATE KEY UPDATE` 补全 `password` + `status`
- [x] `AGENTS.md` 同步更新
- [x] mvn clean compile 通过（97 files）
- [x] Git commit

## 新增文件
```
dev.reboot.enums/
└── ErrorCode.java                # 统一错误码枚举（200/400/401/403/404/409/500）

dev.reboot.exception/
├── BusinessException.java        # 业务异常（携带 ErrorCode）
└── GlobalExceptionHandler.java   # 全局异常处理（BusinessException / @Valid / 兜底500）
```

## 修改文件
| 文件 | 变更 |
|------|------|
| pom.xml | 新增 `spring-boot-starter-validation` 依赖 |
| AuthController.java | `@Valid` 声明式校验，移除手动 if-blank/password-length 检查 |
| DeviceController.java | `@Valid` 声明式校验 |
| LoginDTO.java | 新增 `@NotBlank` + `@Size(min=6)` 注解 |
| DeviceDTO.java | 新增 `@NotBlank` 注解 |
| application.yml | `MYSQL_PASSWORD` 加回默认值 `1zxcvbnm` |
| init.sql | `ON DUPLICATE KEY UPDATE` 补全 `password` + `status` 字段 |
| AGENTS.md | §3 更新至 Day 25→26，移除已完成的模块 |

## 验证结果

| # | 测试 | 结果 |
|---|------|:---:|
| 1 | Login 空密码 → @Valid 拦截 | ✅ 400 |
| 2 | Register 短密码 → @Size 拦截 | ✅ 400 |
| 3 | Device 空名称 → @NotBlank 拦截 | ✅ 400 |
| 4 | Login 正常 | ✅ 200 |
| 5 | Device CRUD 正常 | ✅ 200 |
| 6 | 500 错误 → GlobalExceptionHandler 兜底 | ✅ ApiResponse 格式 |
| 7 | No-auth → 401 | ✅ 401 |
| 8 | VIEWER → /api/users → 403 | ✅ 403 |

## 技术决策

- **ErrorCode 放在 enums/ 非 exception/** — 与现有 `RoleEnum` 风格一致
- **GlobalExceptionHandler 不做 AccessDenied 处理** — RBAC 由 `AuthInterceptor` 直接返回 ApiResponse，不抛异常
- **校验迁移策略** — 保留 Service 层的业务校验（如用户名重复），仅将格式校验移入 DTO 注解

## 代码统计
- 新增 Java 文件：3
- 修改 Java 文件：3
- 新增依赖：1（spring-boot-starter-validation）
- 总编译文件：97（+3）

---

## 明日计划
- Day 27: 项目联调测试 + 文档同步 + 准备进入第三阶段
