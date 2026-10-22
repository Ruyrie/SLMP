package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记出雏（API-B31）。
 */
public class HatchRequest {

    @Nullable
    private String hatchDate;
    @Nullable
    private Integer hatchedCount;
    @Nullable
    private Integer healthyCount;
    @Nullable
    private Boolean transferToBatch;
    @Nullable
    private Long targetPenId;
    @Nullable
    private Long offspringBreedId;
    @Nullable
    private Boolean capacityConfirmed;
    @Nullable
    private Integer version;

    public HatchRequest setHatchDate(@Nullable String hatchDate) {
        this.hatchDate = hatchDate;
        return this;
    }

    public HatchRequest setHatchedCount(@Nullable Integer hatchedCount) {
        this.hatchedCount = hatchedCount;
        return this;
    }

    public HatchRequest setHealthyCount(@Nullable Integer healthyCount) {
        this.healthyCount = healthyCount;
        return this;
    }

    public HatchRequest setTransferToBatch(@Nullable Boolean transferToBatch) {
        this.transferToBatch = transferToBatch;
        return this;
    }

    public HatchRequest setTargetPenId(@Nullable Long targetPenId) {
        this.targetPenId = targetPenId;
        return this;
    }

    public HatchRequest setOffspringBreedId(@Nullable Long offspringBreedId) {
        this.offspringBreedId = offspringBreedId;
        return this;
    }

    public HatchRequest setCapacityConfirmed(@Nullable Boolean capacityConfirmed) {
        this.capacityConfirmed = capacityConfirmed;
        return this;
    }

    public HatchRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getHatchDate() {
        return hatchDate;
    }

    @Nullable
    public Integer getHatchedCount() {
        return hatchedCount;
    }

    @Nullable
    public Integer getHealthyCount() {
        return healthyCount;
    }

    @Nullable
    public Boolean getTransferToBatch() {
        return transferToBatch;
    }

    @Nullable
    public Long getTargetPenId() {
        return targetPenId;
    }

    @Nullable
    public Long getOffspringBreedId() {
        return offspringBreedId;
    }

    @Nullable
    public Boolean getCapacityConfirmed() {
        return capacityConfirmed;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
