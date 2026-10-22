package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D27 通知场长的 data：notified_names（刘芳）。
 */
public class NotifyResultVO {

    @Nullable
    private List<String> notifiedNames;

    @NonNull
    public List<String> getNotifiedNames() {
        return notifiedNames == null ? Collections.emptyList() : notifiedNames;
    }
}
