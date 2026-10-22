package com.zhimu.slmp.today.model.request;

import androidx.annotation.Nullable;

/**
 * 全部标为已读（API-T14），与当前筛选一致；为空的字段不序列化。
 */
public class MessageReadAllRequest {

    @Nullable
    private final String category;
    @Nullable
    private final Long farmId;

    public MessageReadAllRequest(@Nullable String category, @Nullable Long farmId) {
        this.category = category;
        this.farmId = farmId;
    }

    @Nullable
    public String getCategory() {
        return category;
    }

    @Nullable
    public Long getFarmId() {
        return farmId;
    }
}
