package com.zhimu.slmp.base.mvvm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 页面状态：对应 02 号规范 7.19 节的骨架屏与状态占位，由 StateLayout 渲染。
 */
public final class UiState {

    public enum Status {
        /** 首次加载，显示骨架屏 */
        LOADING,
        /** 有数据 */
        CONTENT,
        /** 空状态 */
        EMPTY,
        /** 加载失败（U03 加载失败版式），带文案与 trace_id */
        ERROR,
        /** 无权查看（U03 无权查看版式），带原因 */
        NO_PERMISSION
    }

    private final Status status;
    @Nullable
    private final String title;
    @Nullable
    private final String message;
    @Nullable
    private final String traceId;
    private final int errorCode;

    private UiState(Status status, @Nullable String title, @Nullable String message,
                    @Nullable String traceId, int errorCode) {
        this.status = status;
        this.title = title;
        this.message = message;
        this.traceId = traceId;
        this.errorCode = errorCode;
    }

    public static UiState loading() {
        return new UiState(Status.LOADING, null, null, null, 0);
    }

    public static UiState content() {
        return new UiState(Status.CONTENT, null, null, null, 0);
    }

    /** 空状态；title、message 为空时由 StateLayout 使用页面配置的默认文案 */
    public static UiState empty(@Nullable String title, @Nullable String message) {
        return new UiState(Status.EMPTY, title, message, null, 0);
    }

    public static UiState error(@Nullable String message, @Nullable String traceId, int errorCode) {
        return new UiState(Status.ERROR, null, message, traceId, errorCode);
    }

    public static UiState noPermission(@Nullable String reason, int errorCode) {
        return new UiState(Status.NO_PERMISSION, null, reason, null, errorCode);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getTraceId() {
        return traceId;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public boolean is(Status s) {
        return status == s;
    }
}
