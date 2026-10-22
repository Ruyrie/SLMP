package com.zhimu.slmp.breed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.network.GsonFactory;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

/**
 * 登记表单的共用规则：数字解析、超时判断、幂等键（Idempotency-Key）沿用与重建。
 */
public final class SubmitSupport {

    public static final String MSG_NETWORK_KEEP = "网络连接失败，已保留填写的内容";
    public static final String MSG_RETRY = "重试";
    public static final String MSG_REFRESH = "刷新";
    public static final String MSG_NOT_SAVED = "提交未成功，请重新提交";
    public static final String MSG_NO_PERMISSION = "你已没有这项操作的权限";
    public static final String MSG_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String MSG_NOT_FOUND = "记录不存在或已删除";
    public static final String MSG_SAVED_EDIT = "已保存修改";

    private SubmitSupport() {
    }

    /** 整数文本（允许千分位逗号）；不是整数时返回 null */
    @Nullable
    public static Integer parseInt(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().trim().replace(",", "");
        if (s.isEmpty() || !s.matches("^\\d{1,9}$")) {
            return null;
        }
        return Integer.parseInt(s);
    }

    @Nullable
    public static BigDecimal parseDecimal(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isBlank(@Nullable CharSequence text) {
        return text == null || text.toString().trim().isEmpty();
    }

    /** 提交超时：需要先调 API-B32 确认是否已保存 */
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
     * 幂等键：同一份请求内容（网络重试、超时确认）沿用；内容有任何改动（含 42206 后勾选确认）重新生成。
     * 键与请求快照存 SavedStateHandle，进程回收后恢复。
     */
    public static final class IdemKeeper {
        private static final Gson GSON = GsonFactory.get();
        @NonNull
        private final SavedStateHandle handle;
        @NonNull
        private final String keyName;
        @NonNull
        private final String snapshotName;

        public IdemKeeper(@NonNull SavedStateHandle handle, @NonNull String name) {
            this.handle = handle;
            this.keyName = "idem_" + name;
            this.snapshotName = "idem_snapshot_" + name;
        }

        /** 按请求体取得幂等键 */
        @NonNull
        public String keyFor(@NonNull Object request) {
            String json = GSON.toJson(request);
            String key = handle.get(keyName);
            if (key == null || !json.equals(handle.get(snapshotName))) {
                key = IdemKey.newKey();
                handle.set(keyName, key);
                handle.set(snapshotName, json);
            }
            return key;
        }

        /** 最近一次提交使用的键（API-B32 的 client_request_id） */
        @Nullable
        public String current() {
            return handle.get(keyName);
        }

        /** 业务错误后用户修改内容重提时换新键 */
        public void reset() {
            handle.remove(keyName);
            handle.remove(snapshotName);
        }
    }
}
