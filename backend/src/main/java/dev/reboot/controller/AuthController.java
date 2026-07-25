package dev.reboot.controller;

import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.LoginDTO;
import dev.reboot.dto.UserVO;
import dev.reboot.service.AuthService;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 控制器 —— 登录、注册。
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

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@RequestBody LoginDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }
        if (dto.getPassword().length() < 6) {
            return ApiResponse.error(400, "密码长度至少 6 位");
        }

        UserVO vo = authService.register(dto);
        if (vo == null) {
            return ApiResponse.error(409, "用户名已存在");
        }
        return ApiResponse.ok("注册成功", vo);
    }
}
