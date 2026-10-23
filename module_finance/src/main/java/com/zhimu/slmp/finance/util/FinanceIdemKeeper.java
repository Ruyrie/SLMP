package com.zhimu.slmp.finance.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.network.GsonFactory;

/**
 * 幂等键（Idempotency-Key，技术准备文档 2.4 通用约定）：点击提交且校验通过时生成并存入 SavedStateHandle；
 * 同一份请求内容（超时、断网重试，API-E43 查询）沿用；表单内容变化或明确失败、成功后作废。
 */
public final class FinanceIdemKeeper {

    private final String keyName;
    private final String snapshotName;
    private final Gson gson = GsonFactory.get();
    @Nullable
    private SavedStateHandle handle;
    @Nullable
    private String key;
    @Nullable
    private String snapshot;

    public FinanceIdemKeeper(@NonNull String name) {
        this.keyName = "finance_idem_" + name;
        this.snapshotName = "finance_idem_snapshot_" + name;
    }

    public void attach(@NonNull SavedStateHandle h) {
        handle = h;
        key = h.get(keyName);
        snapshot = h.get(snapshotName);
    }

    /** 内容与待确认键相同则沿用，否则生成新键 */
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

    public void clear() {
        key = null;
        snapshot = null;
        persist();
    }

    private void persist() {
        if (handle != null) {
            handle.set(keyName, key);
            handle.set(snapshotName, snapshot);
        }
    }
}
