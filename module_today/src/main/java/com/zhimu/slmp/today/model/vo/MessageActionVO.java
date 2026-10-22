package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 消息上的可处理动作：邀请（INVITE，API-F21、F22）与转让（TRANSFER，API-F30、F31）。
 */
public class MessageActionVO {

    /** INVITE / TRANSFER */
    @Nullable
    private String actionType;
    /** 邀请 ID 或转让 ID */
    private long actionId;
    /** PENDING / ACCEPTED / REJECTED / EXPIRED */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String roleText;
    @Nullable
    private String expireAt;
    private boolean canAccept;

    @Nullable
    public String getActionType() {
        return actionType;
    }

    public long getActionId() {
        return actionId;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }

    @Nullable
    public String getExpireAt() {
        return expireAt;
    }

    public boolean isCanAccept() {
        return canAccept;
    }
}
