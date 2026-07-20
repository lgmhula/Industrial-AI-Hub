package dev.reboot.common;

/**
 * 统一 API 响应封装。
 *
 * <p>所有 Controller 返回此对象，前端可统一解析。</p>
 *
 * @param <T> 响应数据类型
 * @author hula0710
 * @since 2026-07-20
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "OK", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}