package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mq.SseEmitterRegistry;
import dev.reboot.service.SiteAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * AI 巡检日报 SSE 推送控制器 — 浏览器建连入口（Day 85 Phase 6，ADR 0031 §5.2/§5.5/§9）。
 *
 * <p>本类是 ADR 0031 六段链路中「SSE/WebSocket」段的 Controller 层，承接浏览器
 * EventSource 建连请求，通过 JWT 解析 userId + {@link SiteAccessService} 解析站点范围，
 * 调用 {@link SseEmitterRegistry#register} 建立 30min timeout 的 SseEmitter；Spring MVC
 * 进入异步响应模式后由调度器提交首帧。Redis Pub/Sub fanout 到达后，各实例通过本地
 * Gateway 向自己的连接推送日报。</p>
 *
 * <h3>鉴权链路（ADR 0031 §5.2/§5.5）</h3>
 * <pre>
 * 浏览器 GET /api/push/inspection (Authorization: Bearer &lt;JWT&gt;)
 *   ↓
 * JwtAuthFilter 解析 JWT → request.setAttribute("userId", ...)
 *   ↓
 * AuthInterceptor @RequireRole(VIEWER+) 校验角色
 *   ↓
 * InspectionPushController.subscribe()
 *   ├─ currentUserId(request) 从 request attribute 读 userId
 *   ├─ siteAccessService.accessibleSiteIds(userId) 解析站点
 *   │   ├─ null = ADMIN 全站点 → registry.register(userId, List.of())
 *   │   ├─ 非空 = 用户站点集合 → registry.register(userId, siteIds)
 *   │   └─ 空 List = 无任何站点 → 403 拒绝（P0 安全：空 siteIds 在 SseEmitterSession
 *   │       中被当作 ADMIN 全站点语义，会导致无站点用户收到所有日报）
 *   └─ 返回 SseEmitter → 浏览器建立 EventSource 连接
 * </pre>
 *
 * <h3>安全边界（ADR 0031 §5.5）</h3>
 * <ul>
 *   <li>SSE 端点必须走 JWT Filter + AuthInterceptor，禁止匿名建立 emitter；</li>
 *   <li>Controller 只在建连时解析一次 userId + siteIds 并绑定到 emitter，
     *       后续推送只认 emitter 绑定的 siteIds，不信任消息中的 triggeredByUserId；</li>
 *   <li>非 ADMIN 且无站点访问权的用户拒绝建连（403），避免 P0 跨站点泄漏缺陷。</li>
 * </ul>
 *
 * <h3>审计（ADR 0031 §9 + Flyway V14）</h3>
 * <p>{@code @OperationLog(PUSH/SSE)} 记录建连事件；OperationLogAspect 在方法返回后
 * 异步写入 operation_log 表（成功/失败均记录）。不使用 {@code {ret}} 占位符
 * —— SseEmitter 的 toString 不含业务摘要，{ret} 无意义。</p>
 *
 * <h3>边界约束</h3>
 * <ul>
 *   <li>{@code @Profile("!test")} —— SseEmitterRegistry 在 test profile 不注入，
 *       本 Controller 同步排除，避免 test context 缺 bean 启动失败；
 *       单测用 Mockito 直接实例化，绕过 Spring context。</li>
 *   <li>不处理断线重连 —— 浏览器 EventSource 原生自动重连（指数退避），
 *       重连时重新走 JWT Filter + 本 Controller 建立新 emitter。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 85, Phase 6)
 */
@RestController
@RequestMapping("/api/push")
@Profile("!test")
@Tag(name = "15-巡检推送", description = "AI 巡检日报 SSE 推送")
public class InspectionPushController {

    private static final Logger log = LoggerFactory.getLogger(InspectionPushController.class);

    private final SseEmitterRegistry registry;
    private final SiteAccessService siteAccessService;

    public InspectionPushController(SseEmitterRegistry registry, SiteAccessService siteAccessService) {
        this.registry = registry;
        this.siteAccessService = siteAccessService;
    }

    /**
     * 建立巡检日报 SSE 连接（ADR 0031 §5.2）。
     *
     * <p>浏览器用 {@code new EventSource('/api/push/inspection', { headers: { Authorization: 'Bearer ...' } })}
     * 或在 URL 携带 token 建连。返回的 SseEmitter 由 Spring MVC 持有，
     * Redis fanout 后各实例的本地 Gateway 会按 siteIds 路由日报到该 emitter。</p>
     *
     * @param request HTTP 请求（JwtAuthFilter 已注入 userId attribute）
     * @return 30min timeout 的 SseEmitter
     * @throws BusinessException 403 当非 ADMIN 用户无任何站点访问权时
     */
    @GetMapping(value = "/inspection", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @OperationLog(operationType = "PUSH", targetType = "SSE",
            description = "建立巡检日报 SSE 连接")
    @Operation(summary = "订阅巡检日报 SSE 推送（需登录，VIEWER+）")
    public SseEmitter subscribe(HttpServletRequest request) {
        Long userId = currentUserId(request);
        List<Long> siteIds = resolveAuthorizedSites(userId);

        // ADMIN (null) → 传 List.of()（SseEmitterSession 空 siteIds = ADMIN 全站点语义）
        // 非 ADMIN 有站点 → 传实际 siteIds（canReceive 按交集匹配）
        List<Long> registrySiteIds = siteIds == null ? List.of() : siteIds;
        SseEmitter emitter = registry.register(userId, registrySiteIds);
        log.info("SSE 巡检日报连接建立 userId={} siteIds={}", userId, registrySiteIds);
        return emitter;
    }

    /**
     * 仅做登录/角色/站点权限探测，不创建 emitter。
     *
     * <p>浏览器 EventSource 不能可靠读取 403 body，前端在建立长连接前先用普通 GET
     * 调本端点；成功即表示可以继续建立 SSE。</p>
     */
    @GetMapping("/inspection/access")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "探测巡检日报 SSE 权限（不创建连接）")
    public ApiResponse<Map<String, Object>> access(HttpServletRequest request) {
        Long userId = currentUserId(request);
        resolveAuthorizedSites(userId);
        return ApiResponse.ok("巡检日报 SSE 可订阅", Map.of("authorized", true));
    }

    private List<Long> resolveAuthorizedSites(Long userId) {
        List<Long> siteIds = siteAccessService.accessibleSiteIds(userId);
        // P0 安全防护：空 siteIds 在 SseEmitterSession 中表示 ADMIN 全站点，
        // 非 ADMIN 无站点时必须拒绝，否则会跨站点泄漏。
        if (siteIds != null && siteIds.isEmpty()) {
            log.warn("拒绝 SSE：用户 {} 非 ADMIN 且无站点访问权", userId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "无可访问站点，无法订阅巡检日报");
        }
        return siteIds;
    }

    /** 从 JWT Filter 注入的 request attribute 读取当前登录用户 ID。 */
    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }
}
