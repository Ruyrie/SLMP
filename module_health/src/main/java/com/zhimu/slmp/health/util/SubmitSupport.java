package com.zhimu.slmp.health.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.network.GsonFactory;

import java.net.SocketTimeoutException;

/**
 * 登记提交的共用规则：超时判断（超时后用 API-D41 查询）、幂等键沿用与重建（存 SavedStateHandle，旋转后保留）。
 */
public final class SubmitSupport {

    public static final String MSG_NETWORK_KEEP = "网络连接已断开，已填写的内容会保留。恢复网络后请重新提交。";
    public static final String MSG_SUBMIT_FAILED = "提交没有成功，请重试";
    public static final String MSG_DATA_CHANGED = "内容已更新，请刷新后再修改";
    public static final String ACTION_RETRY = "health_retry_submit";
    public static final String ACTION_REFRESH_LOTS = "health_refresh_lots";

    private SubmitSupport() {
    }

    /** 读超时或连接被断开（结果未知，需要按幂等键确认） */
    public static boolean isTimeout(@NonNull ApiException e) {
        Throwable c = e.getCause();
        while (c != null) {
            if (c instanceof SocketTimeoutException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    /**
     * 幂等键：同一份请求内容（超时重试、API-D41 查询）沿用；内容有任何改动重新生成；成功后清空。
     * 键与请求快照保存在 SavedStateHandle 中。
     */
    public static final class IdemKeeper {
        private static final Gson GSON = GsonFactory.get();
        private static final String KEY = "health_idem_key";
        private static final String SNAPSHOT = "health_idem_snapshot";

        @Nullable
        private SavedStateHandle handle;
        @Nullable
        private String key;
        @Nullable
        private String snapshot;

        public void attach(@NonNull SavedStateHandle handle) {
            this.handle = handle;
            key = handle.get(KEY);
            snapshot = handle.get(SNAPSHOT);
        }

        /** 按请求体取得幂等键 */
        @NonNull
        public String keyFor(@NonNull Object request) {
            String json = GSON.toJson(request);
            if (key == null || !json.equals(snapshot)) {
                key = IdemKey.newKey();
                snapshot = json;
                save();
            }
            return key;
        }

        /** 最近一次提交使用的键（API-D41） */
        @Nullable
        public String current() {
            return key;
        }

        public void reset() {
            key = null;
            snapshot = null;
            save();
        }

        private void save() {
            if (handle != null) {
                handle.set(KEY, key);
                handle.set(SNAPSHOT, snapshot);
            }
        }
    }
}
