package com.zhimu.server.common.api;

import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.error.ErrorCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统一响应：{code, message, data, trace_id}。code 为 0 表示成功，成功时 message 为 "ok"。
 * HTTP 状态码取错误码前三位（成功为 200），由全局异常处理设置。
 */
@Getter
@Setter
@NoArgsConstructor
public class ApiResponse<T> {

    public static final String OK_MESSAGE = "ok";

    private int code;
    private String message;
    private T data;
    private String traceId;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceContext.get();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, OK_MESSAGE, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, OK_MESSAGE, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
