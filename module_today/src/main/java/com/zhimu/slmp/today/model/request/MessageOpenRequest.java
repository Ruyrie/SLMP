package com.zhimu.slmp.today.model.request;

import androidx.annotation.Nullable;

/**
 * 打开消息（API-T15）：从推送打开时带 push_id。
 */
public class MessageOpenRequest {

    @Nullable
    private final Long pushId;

    public MessageOpenRequest(@Nullable Long pushId) {
        this.pushId = pushId;
    }

    @Nullable
    public Long getPushId() {
        return pushId;
    }
}
