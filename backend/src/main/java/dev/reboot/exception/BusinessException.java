package dev.reboot.exception;

import dev.reboot.enums.ErrorCode;

/**
 * 业务异常 — Service 层抛出，由 GlobalExceptionHandler 统一兜底。
 *
 * <p>携带 {@link ErrorCode} 枚举，可附加自定义消息覆盖默认错误描述。</p>
 *
 * @author hula0710
 * @since 2026-07-25
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 使用 ErrorCode 的默认消息。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义消息，但仍以 ErrorCode 的 HTTP 状态码响应。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 携带原始异常（日志用）。 */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
