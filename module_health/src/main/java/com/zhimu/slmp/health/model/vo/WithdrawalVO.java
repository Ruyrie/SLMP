package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D17 休药状态与按药品计算（D05）。员工不输出 remind_text、remind_dates。
 */
public class WithdrawalVO {

    private long batchId;
    private int currentCount;
    @Nullable
    private String batchNo;
    @Nullable
    private String penName;
    /** NONE / IN_WITHDRAWAL / ENDED */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String releaseDate;
    @Nullable
    private String decisiveDrugName;
    @Nullable
    private String lastMedDate;
    @Nullable
    private String remindText;
    @Nullable
    private Integer daysLeft;
    @Nullable
    private Integer withdrawalDays;
    @Nullable
    private Double progress;
    @Nullable
    private List<String> remindDates;
    @Nullable
    private List<WithdrawalDrugVO> drugs;

    public long getBatchId() {
        return batchId;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenName() {
        return penName;
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
    public String getReleaseDate() {
        return releaseDate;
    }

    @Nullable
    public String getDecisiveDrugName() {
        return decisiveDrugName;
    }

    @Nullable
    public String getLastMedDate() {
        return lastMedDate;
    }

    @Nullable
    public String getRemindText() {
        return remindText;
    }

    @Nullable
    public Integer getDaysLeft() {
        return daysLeft;
    }

    @Nullable
    public Integer getWithdrawalDays() {
        return withdrawalDays;
    }

    @Nullable
    public Double getProgress() {
        return progress;
    }

    @NonNull
    public List<String> getRemindDates() {
        return remindDates == null ? Collections.emptyList() : remindDates;
    }

    @NonNull
    public List<WithdrawalDrugVO> getDrugs() {
        return drugs == null ? Collections.emptyList() : drugs;
    }
}
