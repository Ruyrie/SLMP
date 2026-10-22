package com.zhimu.slmp.herd.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.network.GsonFactory;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

/**
 * 登记表单的共用规则：数字解析、超时判断、幂等键（Idempotency-Key）沿用与重建。
 */
public final class FormSupport {

    public static final String MSG_NETWORK_KEEP = "网络已断开，已填写的内容会保留，联网后请重新提交";
    public static final String MSG_SUBMIT_FAILED = "提交没有成功，请重试";

    private FormSupport() {
    }

    /** 整数文本（允许千分位逗号）；不是整数时返回 null */
    @Nullable
    public static Integer parseInt(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().trim().replace(",", "");
        if (s.isEmpty() || !s.matches("^-?\\d{1,9}$")) {
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

    /** 提交超时（需要先调 API-L26 确认是否已保存） */
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
     * 幂等键：同一份请求内容（超时重试、API-L26 查询）沿用；内容有任何改动（含 42206 后勾选确认）重新生成。
     */
    public static final class IdemKeeper {
        private static final Gson GSON = GsonFactory.get();
        @Nullable
        private String key;
        @Nullable
        private String snapshot;

        /** 按请求体取得幂等键 */
        @NonNull
        public String keyFor(@NonNull Object request) {
            String json = GSON.toJson(request);
            if (key == null || !json.equals(snapshot)) {
                key = IdemKey.newKey();
                snapshot = json;
            }
            return key;
        }

        /** 最近一次提交使用的键（API-L26） */
        @Nullable
        public String current() {
            return key;
        }

        public void reset() {
            key = null;
            snapshot = null;
        }
    }
}
