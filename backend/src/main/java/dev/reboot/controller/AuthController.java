package dev.reboot.controller;

import dev.reboot.dto.ApiResponse;
import dev.reboot.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import dev.reboot.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import dev.reboot.dto.LoginRequest;
import dev.reboot.dto.RegisterRequest;
import dev.reboot.dto.UserVO;
import dev.reboot.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 认证 REST 控制器 —— 登录、注册。
 *
 * <p>业务异常统一由 {@link dev.reboot.exception.GlobalExceptionHandler} 处理。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "01-认证", description = "登录 / 注册")
@Tag(name = "01-认证", description = "登录 / 注册")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录 — 返回 JWT Token。 */
    @OperationLog(operationType = "LOGIN", targetType = "USER", description = "用户登录")
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回 JWT Token")
    public ApiResponse<String> login(@Valid @RequestBody LoginRequest dto,
                                      HttpServletRequest request) {
        String token = authService.login(dto);
        // 为 OperationLogAspect 提供 userId（login 不走 interceptor，需手动设置）
        if (token != null && JwtUtils.validateToken(token)) {
            request.setAttribute("userId", JwtUtils.getUserId(token));
        }
        return ApiResponse.ok("登录成功", token);
    }

    /** 注册 — 返回新用户的 UserVO（不含密码）。 */
    @OperationLog(operationType = "CREATE", targetType = "USER", description = "用户注册")
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户，返回 UserVO（不含密码）")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest dto,
                                         HttpServletRequest request) {
        UserVO vo = authService.register(dto);
        if (vo != null) {
            request.setAttribute("userId", vo.getId());
        }
        return ApiResponse.ok("注册成功", vo);
    }
}
