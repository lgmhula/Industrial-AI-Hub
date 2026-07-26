package dev.reboot.controller;

import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.LoginDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录 — 返回 JWT Token。 */
    @PostMapping("/login")
    public ApiResponse<String> login(@Valid @RequestBody LoginDTO dto) {
        String token = authService.login(dto);
        return ApiResponse.ok("登录成功", token);
    }

    /** 注册 — 返回新用户的 UserVO（不含密码）。 */
    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody LoginDTO dto) {
        UserVO vo = authService.register(dto);
        return ApiResponse.ok("注册成功", vo);
    }
}
