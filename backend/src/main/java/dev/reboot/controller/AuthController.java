package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 控制器 —— 登录、注册、登出。
 *
 * @author hula0710
 * @since 2026-07-24
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "01-认证", description = "登录 / 注册 / 登出")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @OperationLog(operationType = "LOGIN", targetType = "USER", description = "用户登录")
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回 JWT Token")
    public ApiResponse<String> login(@Valid @RequestBody LoginRequest dto, HttpServletRequest request) {
        // Controller 仅提取客户端 IP 与 User-Agent；限流/失败计数/审计等安全逻辑在 Service 层
        // （不解析 X-Forwarded-For，反向代理治理后续处理）
        String token = authService.login(dto, request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ApiResponse.ok("登录成功", token);
    }

    @OperationLog(operationType = "CREATE", targetType = "USER", description = "用户注册")
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户，返回 UserVO（不含密码）")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest dto, HttpServletRequest request) {
        UserVO vo = authService.register(dto, request.getRemoteAddr());
        return ApiResponse.ok("注册成功", vo);
    }

    /**
     * 登出（P1-02-A-4）：将当前 token（jti）加入 Redis 黑名单，TTL = 剩余有效期。
     * jti/expiration 由 {@link dev.reboot.security.JwtAuthFilter} 注入 request attributes；
     * 无有效 token（jti 缺失）时幂等返回成功。
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "将当前 token 加入黑名单，立即失效")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        Object jtiObj = request.getAttribute("jti");
        Object expObj = request.getAttribute("expiration");
        if (jtiObj != null) {
            java.time.Duration ttl = java.time.Duration.ofMinutes(30);
            if (expObj instanceof java.util.Date exp) {
                ttl = java.time.Duration.between(java.time.Instant.now(), exp.toInstant());
            }
            authService.logout(jtiObj.toString(), ttl);
        }
        return ApiResponse.ok("已退出登录", null);
    }
}
