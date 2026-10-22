package com.zhimu.slmp.media.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.db.entity.UploadTaskEntity;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.media.upload.UploadItem;

/**
 * 上传队列 upload_task 的状态流转与重试规则（模块07 文档 2.7.3，03 号规范 8.4 节），纯函数，便于单元测试。
 * <pre>
 * WAITING ──→ COMPRESSING（图片压缩 / 视频转码，带进度）──→ UPLOADING（带进度）──→ READY（file_id）
 *    ↑              │                                      │
 *    └── 退避重试 ──┴──────────────── FAILED ←───────────────┘
 *    └──────────── 点按重试（FAILED → WAITING）
 * </pre>
 * 网络错误与 5xx 指数退避重试 3 次（共 4 次尝试），40002 / 40003 等业务错误直接失败。
 */
public final class UploadTaskStates {

    public static final String WAITING = UploadTaskEntity.STATUS_WAITING;
    public static final String COMPRESSING = UploadTaskEntity.STATUS_COMPRESSING;
    public static final String UPLOADING = UploadTaskEntity.STATUS_UPLOADING;
    public static final String READY = UploadTaskEntity.STATUS_READY;
    public static final String FAILED = UploadTaskEntity.STATUS_FAILED;

    /** 失败后自动重试的次数 */
    public static final int MAX_RETRIES = 3;
    /** 进度至少变化这么多才写数据库（Worker 节流） */
    public static final int PROGRESS_STEP = 5;

    public static final String MSG_UPLOAD_FAILED = "上传失败，点按重试";

    private UploadTaskStates() {
    }

    /** 允许的状态变化（同状态只允许 COMPRESSING、UPLOADING 更新进度） */
    public static boolean canTransit(@NonNull String from, @NonNull String to) {
        switch (from) {
            case WAITING:
                return COMPRESSING.equals(to) || UPLOADING.equals(to) || FAILED.equals(to);
            case COMPRESSING:
                return COMPRESSING.equals(to) || UPLOADING.equals(to) || FAILED.equals(to) || WAITING.equals(to);
            case UPLOADING:
                return UPLOADING.equals(to) || READY.equals(to) || FAILED.equals(to) || WAITING.equals(to);
            case FAILED:
                return WAITING.equals(to);
            case READY:
            default:
                return false;
        }
    }

    /** 等待、处理或上传中 */
    public static boolean isActive(@Nullable String status) {
        return WAITING.equals(status) || COMPRESSING.equals(status) || UPLOADING.equals(status);
    }

    /** 数据库状态 → 网格项状态 */
    @NonNull
    public static UploadItem.Status toItemStatus(@Nullable String status) {
        if (status == null) {
            return UploadItem.Status.WAITING;
        }
        switch (status) {
            case COMPRESSING:
                return UploadItem.Status.COMPRESSING;
            case UPLOADING:
                return UploadItem.Status.UPLOADING;
            case READY:
                return UploadItem.Status.DONE;
            case FAILED:
                return UploadItem.Status.FAILED;
            case WAITING:
            default:
                return UploadItem.Status.WAITING;
        }
    }

    /** 失败时的处理 */
    public enum ErrorDecision {
        /** 交给 WorkManager 指数退避后再试（状态回到 WAITING） */
        RETRY,
        /** 直接失败（状态 FAILED，格子显示“上传失败，点按重试”） */
        FAIL
    }

    /**
     * @param code         ApiException 的 code（网络不可用为 -1）
     * @param attemptCount 本次之前已运行的次数（WorkerParameters.getRunAttemptCount()，首次为 0）
     */
    @NonNull
    public static ErrorDecision decide(int code, int attemptCount) {
        if (!isTransient(code)) {
            return ErrorDecision.FAIL;
        }
        return attemptCount < MAX_RETRIES ? ErrorDecision.RETRY : ErrorDecision.FAIL;
    }

    /** 可重试：网络错误、超时、5xx（服务端错误码 5xxxx、HTTP 5xx） */
    public static boolean isTransient(int code) {
        if (code == ErrorCodes.NETWORK) {
            return true;
        }
        if (code == ErrorCodes.FILE_INVALID || code == ErrorCodes.VIDEO_TOO_LARGE) {
            return false;
        }
        return (code >= 50000 && code < 60000) || (code >= 500 && code < 600);
    }

    /** Worker 写进度的节流：变化不少于 5% 或到 100% 时写 */
    public static boolean shouldWriteProgress(int lastWritten, int progress) {
        return progress >= 100 || progress - lastWritten >= PROGRESS_STEP || progress < lastWritten;
    }

    /** 失败时显示在格子上的原因：业务错误用服务端文案，其他为“上传失败，点按重试” */
    @NonNull
    public static String failureText(int code, @Nullable String message) {
        if ((code == ErrorCodes.FILE_INVALID || code == ErrorCodes.VIDEO_TOO_LARGE) && message != null
                && !message.isEmpty()) {
            return message;
        }
        return MSG_UPLOAD_FAILED;
    }
}
