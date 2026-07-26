# Day 29 — 2026-07-26

## 今日目标
- [x] pom.xml 规范化：依赖分组注释 + 统一空行间距
- [x] if 大括号规范：RoleEnum/JwtUtils/DeviceService/UserService/AuthInterceptor 全部修复
- [x] DeviceController.list() 升级为分页+搜索+筛选
- [x] DeviceMapper.searchDevices() — MyBatis 动态 SQL（keyword+deviceType+status）
- [x] DeviceService.searchDevices() — PageHelper 分页封装
- [x] mvn clean compile 通过（104 files）
- [x] Git commit

## 代码规范修复

### pom.xml
- 依赖按模块分组：Spring Boot Core / Persistence / Security
- 每组内依赖保持字母序
- 统一空行间距
- XML 验证通过

### if 大括号
| 文件 | 行 | 修复前 | 修复后 |
|------|:---:|------|------|
| RoleEnum.java | 31 | `if (cond) return r;` | `if (cond) { return r; }` |
| JwtUtils.java | 109 | `if (raw == null) return ...;` | 加 `{ }` |
| DeviceService.java | 83 | `if (status != null) device.set...` | 加 `{ }` |
| UserService.java | 111,114 | `if (user == null\|rows > 0)...` | 统统加 `{ }` |
| AuthInterceptor.java | 81 | `if (userRole == null) continue;` | 加 `{ }` |

## Device 搜索功能

### API 变更
`GET /api/devices` — 返回类型从 `List<DeviceVO>` 升级为 `PageInfo<DeviceVO>`

| 参数 | 类型 | 说明 |
|------|------|------|
| keyword | string? | 模糊匹配设备名称/编码 |
| deviceType | string? | 精确匹配设备类型 |
| status | int? | 精确匹配状态 |
| page | int | 页码（默认1） |
| size | int | 每页条数（默认10） |

### 示例
```http
GET /api/devices?keyword=PLC&deviceType=PLC&status=1&page=1&size=5
```

### 技术实现
- MyBatis `@Select({"<script>", ...})` 数组语法 — 解决字符串内双引号转义问题
- OGNL `keyword.length() > 0` 替代 `keyword != ""` 避免 Java 字符串转义地狱

## 代码统计
- 修改: pom.xml, RoleEnum.java, JwtUtils.java, DeviceService.java, UserService.java, AuthInterceptor.java, DeviceMapper.java, DeviceController.java
- 编译: 104 files, BUILD SUCCESS

---

## 明日计划
- Day 30: 设备数据模块完善（device_data 图表 API + 聚合查询）
