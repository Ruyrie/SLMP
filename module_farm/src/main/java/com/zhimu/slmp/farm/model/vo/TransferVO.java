package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 转让请求（API-F29、F32，FarmDetailVO.pending_transfer、TransferPageVO.pending）。
 */
public class TransferVO {

    public static final String STATUS_PENDING = "PENDING";

    private long transferId;
    private long farmId;
    private long toMemberId;
    @Nullable
    private String farmName;
    @Nullable
    private String fromUserName;
    @Nullable
    private String toName;
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String createdAt;
    @Nullable
    private String expiresAt;
    @Nullable
    private String handledAt;
    @Nullable
    private String cancelReason;

    public long getTransferId() {
        return transferId;
    }

    public long getFarmId() {
        return farmId;
    }

    public long getToMemberId() {
        return toMemberId;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public String getFromUserName() {
        return fromUserName;
    }

    @Nullable
    public String getToName() {
        return toName;
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
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getExpiresAt() {
        return expiresAt;
    }

    @Nullable
    public String getHandledAt() {
        return handledAt;
    }

    @Nullable
    public String getCancelReason() {
        return cancelReason;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
