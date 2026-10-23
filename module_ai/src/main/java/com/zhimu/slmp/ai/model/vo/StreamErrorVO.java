package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SSE error：超时、中断、失败。status：TIMEOUT / FAILED / INTERRUPTED
 */
public class StreamErrorVO {

    private int code;
    private String message;
    private String status;

    public int getCode() {
        return code;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public void setStatus(@Nullable String status) {
        this.status = status;
    }
}
