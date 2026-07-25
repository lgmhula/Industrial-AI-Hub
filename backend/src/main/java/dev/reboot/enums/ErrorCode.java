package dev.reboot.enums;

/**
 * 统一错误码枚举 —— 覆盖项目所有 API 可返回的状态码。
 *
 * <p>用于构造 {@link dev.reboot.exception.BusinessException}，最终由
 * {@link dev.reboot.exception.GlobalExceptionHandler} 转为
 * {@link dev.reboot.dto.ApiResponse}。</p>
 *
 * @author hula0710
 * @since 2026-07-25
 */
public enum ErrorCode {

    /** 操作成功。 */
    SUCCESS(200, "操作成功"),

    /** 请求参数格式或值不合法。 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未认证 — 缺少有效 JWT 或 Token 已过期。 */
    UNAUTHORIZED(401, "请先登录"),

    /** 已认证但无权限 — 角色不满足接口要求。 */
    FORBIDDEN(403, "权限不足"),

    /** 请求的资源不存在（如设备 ID、用户 ID）。 */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 资源冲突 — 唯一索引违反、并发竞态等。
     *
     * <p>对应 HTTP 409 Conflict。</p>
     */
    CONFLICT(409, "资源冲突"),

    /** 服务器内部未预期的错误。 */
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
