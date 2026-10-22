package com.zhimu.slmp.inspect.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.network.GsonFactory;

import java.util.UUID;

/**
 * client_request_id（兼作 Idempotency-Key，文档 2.4.2）：首次点“提交 / 转上报”生成并存 SavedStateHandle，
 * 重试与超时复查沿用；业务错误后内容有改动再生成。
 */
public final class ClientRequestKeeper {

    private static final Gson GSON = GsonFactory.get();

    @NonNull
    private final SavedStateHandle handle;
    @NonNull
    private final String key;

    public ClientRequestKeeper(@NonNull SavedStateHandle handle, @NonNull String key) {
        this.handle = handle;
        this.key = key;
    }

    /** 按请求内容（不含 client_request_id）取得 ID：内容与上次相同时沿用 */
    @NonNull
    public String idFor(@NonNull Object contentWithoutId) {
        String snapshot = GSON.toJson(contentWithoutId);
        String id = handle.get(key);
        String old = handle.get(key + "_snapshot");
        if (id == null || !snapshot.equals(old)) {
            id = UUID.randomUUID().toString();
            handle.set(key, id);
            handle.set(key + "_snapshot", snapshot);
        }
        return id;
    }

    /** 最近一次使用的 ID（超时复查） */
    @Nullable
    public String current() {
        return handle.get(key);
    }

    public void reset() {
        handle.remove(key);
        handle.remove(key + "_snapshot");
    }
}
