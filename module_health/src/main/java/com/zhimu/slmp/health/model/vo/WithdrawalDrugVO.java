package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-D17 按药品计算的一项：tag 为 LATEST（最晚）/ ENDED（已到期）/ NONE。
 */
public class WithdrawalDrugVO {

    private long catalogId;
    private int withdrawalDays;
    @Nullable
    private String drugName;
    @Nullable
    private String lastDate;
    @Nullable
    private String endDate;
    @Nullable
    private String tag;
    @Nullable
    private String tagText;

    public long getCatalogId() {
        return catalogId;
    }

    public int getWithdrawalDays() {
        return withdrawalDays;
    }

    @Nullable
    public String getDrugName() {
        return drugName;
    }

    @Nullable
    public String getLastDate() {
        return lastDate;
    }

    @Nullable
    public String getEndDate() {
        return endDate;
    }

    @Nullable
    public String getTag() {
        return tag;
    }

    @Nullable
    public String getTagText() {
        return tagText;
    }
}
