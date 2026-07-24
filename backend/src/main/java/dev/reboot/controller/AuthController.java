package dev.reboot.controller;

import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.LoginDTO;
import dev.reboot.dto.RegisterResponse;
import dev.reboot.service.AuthService;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 控制器 —— 登录、注册。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/auth/login    —— 登录，返回 JWT Token</li>
 *   <li>POST /api/auth/register —— 注册，返回 RegisterResponse（无密码字段）</li>
 * </ul>
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

    /**
     * 用户登录。
     *
     * <p>请求体示例：
     * <pre>{@code {"username": "admin", "password": "123456"}}</pre>
     */
    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }

        String token = authService.login(dto);
        if (token == null) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        return ApiResponse.ok("登录成功", token);
    }

    /**
     * 用户注册。
     *
     * <p>请求体示例：
     * <pre>{@code {"username": "newuser", "password": "mypassword"}}</pre>
     */
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@RequestBody LoginDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }
        if (dto.getPassword().length() < 6) {
            return ApiResponse.error(400, "密码长度至少 6 位");
        }

        RegisterResponse resp = authService.register(dto);
        if (resp == null) {
            return ApiResponse.error(409, "用户名已存在");
        }
        return ApiResponse.ok("注册成功", resp);
    }
}
