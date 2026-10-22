package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * L02 栏舍卡中的在栏批次。
 */
public class MyPenBatchVO {

    private long batchId;
    private String batchNo;
    private String breedTitle;
    private int qtyInPen;
    private int dayAge;
    private boolean inWithdrawal;
    private String stageName;
    private Integer withdrawalRemainingDays;

    public long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getBreedTitle() {
        return breedTitle;
    }

    public int getQtyInPen() {
        return qtyInPen;
    }

    public int getDayAge() {
        return dayAge;
    }

    public boolean isInWithdrawal() {
        return inWithdrawal;
    }

    @Nullable
    public String getStageName() {
        return stageName;
    }

    @Nullable
    public Integer getWithdrawalRemainingDays() {
        return withdrawalRemainingDays;
    }
}
