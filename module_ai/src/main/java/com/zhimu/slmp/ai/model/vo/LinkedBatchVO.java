package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 可关联 / 已关联的在栏批次（API-K02、K05）
 */
public class LinkedBatchVO {

    private long batchId;
    private String batchNo;
    private String categoryName;
    private String stageName;
    private String breedName;
    private long penId;
    private String penName;
    private int dayAge;
    private int currentCount;
    private String countUnitText;

    public long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getStageName() {
        return stageName;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    public int getDayAge() {
        return dayAge;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    @Nullable
    public String getCountUnitText() {
        return countUnitText;
    }

    public void setBatchId(long batchId) {
        this.batchId = batchId;
    }

    public void setBatchNo(@Nullable String batchNo) {
        this.batchNo = batchNo;
    }

    public void setCategoryName(@Nullable String categoryName) {
        this.categoryName = categoryName;
    }

    public void setStageName(@Nullable String stageName) {
        this.stageName = stageName;
    }

    public void setBreedName(@Nullable String breedName) {
        this.breedName = breedName;
    }

    public void setPenId(long penId) {
        this.penId = penId;
    }

    public void setPenName(@Nullable String penName) {
        this.penName = penName;
    }

    public void setDayAge(int dayAge) {
        this.dayAge = dayAge;
    }

    public void setCurrentCount(int currentCount) {
        this.currentCount = currentCount;
    }

    public void setCountUnitText(@Nullable String countUnitText) {
        this.countUnitText = countUnitText;
    }
}
