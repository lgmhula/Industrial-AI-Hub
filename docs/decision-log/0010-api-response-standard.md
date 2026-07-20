# ADR-0010: ApiResponse<T> 统一响应格式

**日期:** 2026-07-20  
**状态:** Accepted  
**决策者:** hula0710

## 背景

Day 21 的 API 直接返回裸对象（`List<Device>`, `Device`），
错误时依赖 Tomcat 默认 HTML 错误页或 Jackson 序列化的散乱异常。

## 决策

所有 Controller 返回值统一包裹为 `ApiResponse<T>`：

```json
{
  "code": 200,
  "message": "OK",
  "data": { ... }
}
```

## 理由

1. 前端/调用方可统一解析 `code` 判断成功/失败
2. `message` 提供人可读的错误描述
3. 全局异常处理器 (`@RestControllerAdvice`) 自动转换为标准错误响应
4. 接口文档化时字段结构一致

## 后果

- 新增 `dev.reboot.common.ApiResponse<T>`
- 新增 `dev.reboot.common.GlobalExceptionHandler`
- 所有 Controller 重构为返回 `ApiResponse<T>`
- 调用方需要适配 `response.data` 取值
