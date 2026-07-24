# Day 24 — 2026-07-24

## 今日目标
- [x] 实现 RBAC 权限模型：RoleEnum 角色枚举
- [x] 实现 JWT 认证 Filter（JwtAuthFilter）
- [x] 实现权限拦截器（AuthInterceptor）
- [x] 实现 @RequireRole 自定义注解
- [x] JWT Token 携带角色信息（roles claim）
- [x] DeviceController 全部接口加权限控制
- [x] mvn clean compile 通过（91 files）
- [ ] Postman 多角色测试（需启动 Spring Boot + 注册不同角色用户）
- [x] Git commit

## 编码时长
1.5 小时

## 权限架构

```
HTTP Request
  │
  ▼
┌──────────────────┐
│  JwtAuthFilter   │  Filter — 提取 Authorization: Bearer <token>
│  验证 + 注入属性  │  验证通过 → request.setAttribute(userId, username, roles)
└────────┬─────────┘  验证失败 → 记录日志，放行（由 Interceptor 处理 401）
         │
         ▼
┌──────────────────┐
│ AuthInterceptor  │  HandlerInterceptor — 检查 @RequireRole 注解
│  注解 → 角色检查  │  无注解 → 公开接口，放行
└────────┬─────────┘  未登录 → 401
         │            角色不匹配 → 403
         │            通过 → 放行
         ▼
    Controller
```

## 新增文件
```
dev.reboot.enums/
└── RoleEnum.java                   # ADMIN / OPERATOR / VIEWER（含 isAtLeast 层级比较）

dev.reboot.annotation/
└── RequireRole.java                # @RequireRole(RoleEnum.ADMIN) 注解

dev.reboot.security/
├── JwtAuthFilter.java              # JWT 认证 Filter（FilterRegistrationBean 注册）
└── AuthInterceptor.java            # 权限拦截器（含 401/403 JSON 响应）

dev.reboot.config/
└── WebMvcConfig.java               # 注册 Filter + Interceptor（/api/auth/** 白名单）
```

## 修改文件
| 文件 | 变更 |
|------|------|
| JwtUtils.java | `generateToken()` 新增 List\<String\> roles 参数 + `getRoles()` 方法 |
| AuthService.java | 登录时将用户角色写入 JWT |
| DeviceController.java | 五个接口加 @RequireRole 注解 |

## 权限矩阵

| 接口 | 方法 | VIEWER | OPERATOR | ADMIN | 未登录 |
|------|------|:---:|:---:|:---:|:---:|
| GET /api/devices | list() | ✅ | ✅ | ✅ | ❌ 401 |
| GET /api/devices/{id} | getById() | ✅ | ✅ | ✅ | ❌ 401 |
| POST /api/devices | create() | ❌ 403 | ✅ | ✅ | ❌ 401 |
| PUT /api/devices/{id} | update() | ❌ 403 | ✅ | ✅ | ❌ 401 |
| DELETE /api/devices/{id} | delete() | ❌ 403 | ❌ 403 | ✅ | ❌ 401 |
| POST /api/auth/login | — | 🟢 公开 | 🟢 公开 | 🟢 公开 | 🟢 公开 |
| POST /api/auth/register | — | 🟢 公开 | 🟢 公开 | 🟢 公开 | 🟢 公开 |

## 技术决策
- **不引入 Spring Security 全栈** — 使用 HandlerInterceptor + 自定义注解，轻量且可理解
- **角色层级** — ADMIN > OPERATOR > VIEWER，`@RequireRole({VIEWER, ADMIN})` 满足任一即可
- **JWT 携带角色** — 避免每次请求查库（审计 #9 修复）

## Postman 测试
```bash
# 1. 登录默认管理员（init.sql 已预置 admin/admin123 + ADMIN 角色）
POST /api/auth/login {"username":"admin","password":"admin123"}
# → token: eyJ...

# 3. 测试各接口（Header: Authorization: Bearer <token>）
GET    /api/devices          → 200
POST   /api/devices          → 200 (ADMIN)
DELETE /api/devices/1        → 200 (ADMIN)
```

## 明日计划
- Day 25: 用户管理模块 + 分页查询（PageHelper）
