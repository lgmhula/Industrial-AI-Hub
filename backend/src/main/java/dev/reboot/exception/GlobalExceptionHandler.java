package dev.reboot.exception;

import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import jakarta.validation.ConstraintViolationException;
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
 *   <li>{@link HttpMessageNotReadableException} → 400，畸形 JSON / 缺失请求体（TD-038）</li>
 *   <li>{@link AsyncRequestNotUsableException} → 客户端已断开，仅记录调试日志</li>
 *   <li>其他未捕获异常 → 500，隐藏内部细节，打印完整堆栈</li>
 * </ul>
 *
 * @author hula0710
 * @since 2026-07-25
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 — 根据 ErrorCode 返回对应的 HTTP 状态码，ApiResponse body 结构不变。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [{}] : {}", e.getErrorCode().getCode(), e.getMessage());
        HttpStatus httpStatus = HttpStatus.resolve(e.getErrorCode().getCode());
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
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

    /** 缺少必填请求参数 — 返回 400。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return ApiResponse.error(400, "缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型转换失败 — 返回 400。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}={}", e.getName(), e.getValue());
        return ApiResponse.error(400, "参数类型错误: " + e.getName());
    }

    /**
     * 请求体不可读（畸形 JSON / 缺失必填请求体）— 返回 400（TD-038）。
     *
     * <p>这是客户端错误而非服务器故障：此前落入 {@link Exception} 兜底会被
     * 误标为 500 并写入 error.log（Day 102 压测实测畸形 JSON 返回 500）。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.error(400, "请求体格式错误，请检查 JSON 语法与必填字段");
    }

    /**
     * 异步请求的客户端已断开（常见于 SSE 页面离页）。
     *
     * <p>此时 Servlet 响应通道已不可写，处理器必须返回 {@code void}，仅把异常
     * 标记为已处理。若落入通用兜底并尝试写 JSON，会触发二次
     * {@code AsyncRequestNotUsableException} 和误导性的 ERROR 日志。</p>
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        log.debug("异步请求客户端已断开: {}", e.getMessage());
    }

    /** 兜底 — 所有未预期的异常。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }
}
