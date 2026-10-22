package com.zhimu.slmp.network.model;

import androidx.annotation.Nullable;

/**
 * 统一响应：code（0 为成功）、message、data、trace_id（04 号文档 7.1 节）。
 */
public class ApiResponse<T> {

    private int code;
    @Nullable
    private String message;
    @Nullable
    private T data;
    @Nullable
    private String traceId;

    public ApiResponse() {
    }

    public ApiResponse(int code, @Nullable String message, @Nullable T data, @Nullable String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public int getCode() {
        return code;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public String getTraceId() {
        return traceId;
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
