package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.entity.Site;
import dev.reboot.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * Site REST 控制器（P1-01）—— 返回当前用户可访问站点（前端筛选用）。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@RestController
@RequestMapping("/api/sites")
@RequireRole()
@Tag(name = "07-站点", description = "当前用户可访问站点")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    @Operation(summary = "当前用户可访问站点列表（全局管理员=全部）")
    public ApiResponse<List<Site>> listAccessible(HttpServletRequest request) {
        return ApiResponse.ok(siteService.listAccessibleSites(currentUserId(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }
}
