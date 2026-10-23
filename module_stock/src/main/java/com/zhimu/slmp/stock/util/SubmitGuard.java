package com.zhimu.slmp.stock.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.network.GsonFactory;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

/**
 * 提交防重（技术准备文档 2.6.7）：幂等键在点主按钮且校验通过时生成，存 SavedStateHandle（键 idem_key）；
 * 同一份请求内容（网络失败、超时重试）沿用；内容有改动时 {@link #keyFor} 自动换新键；成功或业务错误后清除。
 * 超时后由 StockFormViewModel 用当前键查 API-M42：DONE 按成功处理，PROCESSING 每 2 秒再查、最多 5 次，NOT_FOUND 提示重试并保留键。
 */
public final class SubmitGuard {

    public static final String KEY = "idem_key";
    private static final String SNAPSHOT = "idem_snapshot";

    /** API-M42 轮询间隔与次数（测试中改为 0 毫秒） */
    @VisibleForTesting
    public static long pollIntervalMs = 2000;
    public static final int MAX_POLLS = 5;

    private final Gson gson = GsonFactory.get();
    @Nullable
    private SavedStateHandle handle;
    @Nullable
    private String key;
    @Nullable
    private String snapshot;

    public void attach(@NonNull SavedStateHandle h) {
        handle = h;
        key = h.get(KEY);
        snapshot = h.get(SNAPSHOT);
    }

    /** 按请求体取得幂等键：内容与待确认键相同则沿用，否则生成新键 */
    @NonNull
    public String keyFor(@NonNull Object request) {
        String json = gson.toJson(request);
        if (key == null || !json.equals(snapshot)) {
            key = IdemKey.newKey();
            snapshot = json;
            persist();
        }
        return key;
    }

    @Nullable
    public String current() {
        return key;
    }

    @Nullable
    public String snapshot() {
        return snapshot;
    }

    /** 从草稿恢复待确认的键 */
    public void restore(@Nullable String k, @Nullable String s) {
        key = k;
        snapshot = s;
        persist();
    }

    public void clear() {
        key = null;
        snapshot = null;
        persist();
    }

    private void persist() {
        if (handle != null) {
            handle.set(KEY, key);
            handle.set(SNAPSHOT, snapshot);
        }
    }

    /** 读超时：请求可能已到达服务端，需要先查询提交结果 */
    public static boolean isTimeout(@NonNull ApiException e) {
        Throwable c = e.getCause();
        while (c != null) {
            if (c instanceof SocketTimeoutException || c instanceof InterruptedIOException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }
}
