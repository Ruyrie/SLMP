package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 所有权转让内容（API-T16 payload，msg_type 为 OWNER_TRANSFER）。
 */
public class TransferPayload {

    @Nullable
    private String farmName;
    @Nullable
    private String fromUserName;
    @Nullable
    private String toUserName;
    @Nullable
    private String status;
    @Nullable
    private String expireAt;

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public String getFromUserName() {
        return fromUserName;
    }

    @Nullable
    public String getToUserName() {
        return toUserName;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getExpireAt() {
        return expireAt;
    }
}
