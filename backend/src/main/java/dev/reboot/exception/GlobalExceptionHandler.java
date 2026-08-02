package dev.reboot.exception;

import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 统一将异常转为 {@link ApiResponse}。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>{@link BusinessException} → 按 ErrorCode 定义的状态码和消息返回</li>
 *   <li>{@link MethodArgumentNotValidException} → 400，列出所有字段校验失败信息</li>
 *   <li>其他未捕获异常 → 500，隐藏内部细节，打印完整堆栈</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-25
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 — 携带明确的 ErrorCode。 */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK) // 业务异常本身不是 HTTP 异常，status 由 response body 的 code 决定
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [{}] : {}", e.getErrorCode().getCode(), e.getMessage());
        return ApiResponse.error(e.getErrorCode().getCode(), e.getMessage());
    }

    /** @Valid 校验失败 — 返回 400 并列出所有字段错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ApiResponse.error(400, "参数校验失败: " + detail);
    }

    /** @Validated 参数校验失败（@Min/@Max 等）— 返回 400。 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return ApiResponse.error(400, "参数校验失败: " + e.getMessage());
    }

    /** 兜底 — 所有未预期的异常。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }
}
