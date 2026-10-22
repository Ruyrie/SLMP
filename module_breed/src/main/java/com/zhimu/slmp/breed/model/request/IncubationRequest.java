package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记入孵（API-B26）。
 */
public class IncubationRequest {

    @Nullable
    private Long breedingBatchId;
    @Nullable
    private String setDate;
    @Nullable
    private Integer setCount;

    public IncubationRequest setBreedingBatchId(@Nullable Long breedingBatchId) {
        this.breedingBatchId = breedingBatchId;
        return this;
    }

    public IncubationRequest setSetDate(@Nullable String setDate) {
        this.setDate = setDate;
        return this;
    }

    public IncubationRequest setSetCount(@Nullable Integer setCount) {
        this.setCount = setCount;
        return this;
    }

    @Nullable
    public Long getBreedingBatchId() {
        return breedingBatchId;
    }

    @Nullable
    public String getSetDate() {
        return setDate;
    }

    @Nullable
    public Integer getSetCount() {
        return setCount;
    }
}
