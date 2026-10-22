package com.zhimu.slmp.today.model.request;

import androidx.annotation.Nullable;

/**
 * 忽略待办（API-T09）：原因去首尾空格后 1–100 字。
 */
public class TodoIgnoreRequest {

    @Nullable
    private final String reason;
    private final int version;

    public TodoIgnoreRequest(@Nullable String reason, int version) {
        this.reason = reason;
        this.version = version;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public int getVersion() {
        return version;
    }
}
