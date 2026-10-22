package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 修改断奶（API-B21）。
 */
public class WeaningUpdateRequest {

    @Nullable
    private String weanDate;
    @Nullable
    private Integer weanedCount;
    @Nullable
    private BigDecimal litterWeight;
    @Nullable
    private Integer version;

    public WeaningUpdateRequest setWeanDate(@Nullable String weanDate) {
        this.weanDate = weanDate;
        return this;
    }

    public WeaningUpdateRequest setWeanedCount(@Nullable Integer weanedCount) {
        this.weanedCount = weanedCount;
        return this;
    }

    public WeaningUpdateRequest setLitterWeight(@Nullable BigDecimal litterWeight) {
        this.litterWeight = litterWeight;
        return this;
    }

    public WeaningUpdateRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getWeanDate() {
        return weanDate;
    }

    @Nullable
    public Integer getWeanedCount() {
        return weanedCount;
    }

    @Nullable
    public BigDecimal getLitterWeight() {
        return litterWeight;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
