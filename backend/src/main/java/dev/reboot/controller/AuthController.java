package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 控制器 —— 登录、注册。
 *
 * @author hula0710
 * @since 2026-07-24
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "01-认证", description = "登录 / 注册")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @OperationLog(operationType = "LOGIN", targetType = "USER", description = "用户登录")
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回 JWT Token")
    public ApiResponse<String> login(@Valid @RequestBody LoginRequest dto) {
        String token = authService.login(dto);
        return ApiResponse.ok("登录成功", token);
    }

    @OperationLog(operationType = "CREATE", targetType = "USER", description = "用户注册")
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户，返回 UserVO（不含密码）")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest dto) {
        UserVO vo = authService.register(dto);
        return ApiResponse.ok("注册成功", vo);
    }
}
