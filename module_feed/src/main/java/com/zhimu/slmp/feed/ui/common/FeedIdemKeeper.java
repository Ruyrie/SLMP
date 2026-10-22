package com.zhimu.slmp.feed.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.network.GsonFactory;

/**
 * 待确认的幂等键（Idempotency-Key）：点提交且校验通过时生成，存 SavedStateHandle（旋转、进程回收）与草稿（离开后再进入）；
 * 同一份请求内容（超时、断网重试，API-G08 查询）沿用；内容有任何改动（改动字段）或明确失败后清除、下次换新键。
 */
public final class FeedIdemKeeper {

    private static final String KEY = "feed_idem_key";
    private static final String SNAPSHOT = "feed_idem_snapshot";

    private final Gson gson = GsonFactory.get();
    @Nullable
    private SavedStateHandle handle;
    @Nullable
    private String key;
    @Nullable
    private String snapshot;

    /** 绑定 SavedStateHandle 并恢复其中的待确认键 */
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

    /** 当前待确认的键（API-G08 查询）；没有时为 null */
    @Nullable
    public String current() {
        return key;
    }

    @Nullable
    public String snapshot() {
        return snapshot;
    }

    /** 请求内容是否与待确认键对应的内容一致 */
    public boolean matches(@NonNull Object request) {
        return key != null && gson.toJson(request).equals(snapshot);
    }

    /** 从草稿恢复 */
    public void restore(@Nullable String k, @Nullable String s) {
        key = k;
        snapshot = s;
        persist();
    }

    /** 成功、明确失败或改动字段后清除 */
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
}
