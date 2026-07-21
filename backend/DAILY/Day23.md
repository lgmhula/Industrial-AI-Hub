# Day 23 — 2026-07-21

## 今日目标
- [x] 实现 JWT 工具类（生成 token、验证 token、解析 token）
- [x] 实现登录接口 POST /api/auth/login
- [x] 实现注册接口 POST /api/auth/register
- [x] 密码 BCrypt 加密
- [x] mvn clean compile 通过（83 files）
- [ ] 用 Postman 测试完整登录流程（需启动 Spring Boot + MySQL）
- [x] Git commit

## 编码时长
1.5 小时

## 新增依赖
| 依赖 | 版本 | 用途 |
|------|------|------|
| io.jsonwebtoken:jjwt-api | 0.12.6 | JWT 核心 API |
| io.jsonwebtoken:jjwt-impl | 0.12.6 | JWT 实现 (runtime) |
| io.jsonwebtoken:jjwt-jackson | 0.12.6 | JWT Jackson 序列化 (runtime) |
| spring-security-crypto | (SB 3.5 管理) | BCrypt 密码加密 |

## 新增文件
```
dev.reboot.util/
└── JwtUtils.java               # JWT 生成/验证/解析

dev.reboot.service/
└── AuthService.java            # 登录验证 + 注册 + BCrypt

dev.reboot.controller/
└── AuthController.java         # POST /api/auth/login, /api/auth/register

dev.reboot.config/
└── SecurityConfig.java         # BCryptPasswordEncoder Bean
```

## 关键实现细节

### JWT
- 签名算法：HmacSHA256（256-bit secret key）
- 有效期：24 小时
- Claims：subject=username, userId
- 异常处理：JwtException → validateToken 返回 false

### BCrypt
- 注册时：`passwordEncoder.encode(rawPassword)`
- 登录时：`passwordEncoder.matches(rawPassword, encodedPassword)`
- 强度：BCrypt 默认 10 rounds

### API 端点
| 方法 | 路径 | 请求体 | 成功响应 |
|------|------|--------|----------|
| POST | /api/auth/register | `{"username":"...", "password":"..."}` | `{code:200, data:{id,username,...}}` |
| POST | /api/auth/login | `{"username":"...", "password":"..."}` | `{code:200, data:"<jwt>"}` |

### 边界处理
- 空用户名/密码 → 400
- 密码少于 6 位 → 400（仅注册）
- 用户名不存在/密码错误/用户禁用 → 401
- 用户名已存在 → 409

## Postman 测试步骤
1. 确保 MySQL(Docker 3307) 已启动，`reboot` 库已初始化
2. `mvn spring-boot:run` 启动后端
3. POST http://localhost:8080/api/auth/register → 创建用户
4. POST http://localhost:8080/api/auth/login → 获取 JWT
5. 验证 token：https://jwt.io 粘贴查看 payload

## 明日计划
- Day 24: RBAC 权限模型 + JWT Filter 拦截器 + @RequireRole 注解
