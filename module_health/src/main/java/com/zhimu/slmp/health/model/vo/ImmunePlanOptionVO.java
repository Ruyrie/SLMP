package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-D11 登记接种的计划下拉项（种畜栏舍项无批次）。
 */
public class ImmunePlanOptionVO {

    private long planId;
    private int currentCount;
    @Nullable
    private String targetType;
    @Nullable
    private String penName;
    @Nullable
    private String vaccineName;
    @Nullable
    private String planDate;
    @Nullable
    private String planDateText;
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private String immuneRound;

    public long getPlanId() {
        return planId;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    @Nullable
    public String getTargetType() {
        return targetType;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getVaccineName() {
        return vaccineName;
    }

    @Nullable
    public String getPlanDate() {
        return planDate;
    }

    @Nullable
    public String getPlanDateText() {
        return planDateText;
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
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getImmuneRound() {
        return immuneRound;
    }
}
