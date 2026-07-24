# Day 25 — 2026-07-24

## 今日目标
- [x] 实现用户管理模块：用户列表、查询、编辑、删除
- [x] 分页查询（PageHelper 2.1.0）
- [x] 用户状态启用/禁用
- [x] 管理员权限保护（类级 @RequireRole(ADMIN)）
- [x] mvn clean compile 通过（94 files）
- [x] Git commit

## 编码时长
1 小时

## 新增依赖
| 依赖 | 版本 | 用途 |
|------|------|------|
| com.github.pagehelper:pagehelper-spring-boot-starter | 2.1.0 | MyBatis 分页插件 |

## 新增文件
```
dev.reboot.controller/
└── UserController.java            # /api/users CRUD + 分页

dev.reboot.dto/
└── UserUpdateDTO.java             # 用户编辑请求（email, phone）
```

## 修改文件
| 文件 | 变更 |
|------|------|
| pom.xml | 新增 PageHelper 依赖 |
| UserMapper.java | 新增 `updateStatus()` 方法 |
| UserService.java | 新增 `listPage()` 分页 + `update()` + `toggleStatus()` + `delete()` |

## API 端点

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|:---:|
| GET | /api/users?page=1&size=10 | 分页用户列表（UserVO，无密码） | ADMIN |
| GET | /api/users/{id} | 按 ID 查询用户 | ADMIN |
| PUT | /api/users/{id} | 编辑用户（email, phone） | ADMIN |
| PUT | /api/users/{id}/status | 切换启用/禁用状态 | ADMIN |
| DELETE | /api/users/{id} | 删除用户 | ADMIN |

## 分页响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 25,
    "list": [...],
    "pageNum": 1,
    "pageSize": 10,
    "pages": 3
  }
}
```

---

## Postman 测试用例

### 前置条件
- MySQL (Docker 3307) 已启动，`reboot` 库已初始化
- `export JWT_SECRET="DevOnly-DefaultKey-DoNotUseInProduction-ChangeMe-256bit!"`
- `mvn spring-boot:run` 启动后端

### 用例 1：登录获取 Token
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```
> 预期：200 OK，`data` 字段返回 JWT 字符串，复制备用。
> 变量：`TOKEN = response.data`

### 用例 2：分页查询用户列表
```http
GET http://localhost:8080/api/users?page=1&size=5
Authorization: Bearer {{TOKEN}}
```
> 预期：200 OK，`data.total` ≥ 1，`data.list` 最多 5 条，每条无 `password` 字段

### 用例 3：按 ID 查询用户
```http
GET http://localhost:8080/api/users/1
Authorization: Bearer {{TOKEN}}
```
> 预期：200 OK，`data.username = "admin"`，无 `password` 字段

### 用例 4：编辑用户信息
```http
PUT http://localhost:8080/api/users/1
Authorization: Bearer {{TOKEN}}
Content-Type: application/json

{
  "email": "admin@industrial-hub.com",
  "phone": "13800000001"
}
```
> 预期：200 OK，响应中 `email` 和 `phone` 已更新

### 用例 5：切换用户状态（禁用）
```http
PUT http://localhost:8080/api/users/1/status
Authorization: Bearer {{TOKEN}}
```
> 预期：200 OK。再次调用 GET /api/users/1 确认 `status = 0`

### 用例 6：切换用户状态（启用）
```http
PUT http://localhost:8080/api/users/1/status
Authorization: Bearer {{TOKEN}}
```
> 预期：200 OK。再次调用 GET /api/users/1 确认 `status = 1`

### 用例 7：无权限访问（未登录）
```http
GET http://localhost:8080/api/users
```
> 预期：401 Unauthorized，`{"code":401,"message":"请先登录"}`

### 用例 8：无权限访问（非 ADMIN）
```http
# 先用 /api/auth/register 注册一个 VIEWER 用户
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "viewer_test",
  "password": "123456"
}

# 登录获取 VIEWER token
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "viewer_test",
  "password": "123456"
}

# 用 VIEWER token 访问管理接口
GET http://localhost:8080/api/users
Authorization: Bearer {{VIEWER_TOKEN}}
```
> 预期：403 Forbidden，`{"code":403,"message":"权限不足"}`

### 用例 9：删除用户
```http
DELETE http://localhost:8080/api/users/2
Authorization: Bearer {{TOKEN}}
```
> 预期：200 OK（如果用户存在），或 404

---

## 明日计划
- Day 26: 全局异常处理 @ControllerAdvice + 参数校验 @Valid
