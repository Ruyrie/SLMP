package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 登记断奶（API-B20）。
 */
public class WeaningRequest {

    @Nullable
    private Long animalId;
    @Nullable
    private Integer animalVersion;
    @Nullable
    private String weanDate;
    @Nullable
    private Integer weanedCount;
    @Nullable
    private BigDecimal litterWeight;

    public WeaningRequest setAnimalId(@Nullable Long animalId) {
        this.animalId = animalId;
        return this;
    }

    public WeaningRequest setAnimalVersion(@Nullable Integer animalVersion) {
        this.animalVersion = animalVersion;
        return this;
    }

    public WeaningRequest setWeanDate(@Nullable String weanDate) {
        this.weanDate = weanDate;
        return this;
    }

    public WeaningRequest setWeanedCount(@Nullable Integer weanedCount) {
        this.weanedCount = weanedCount;
        return this;
    }

    public WeaningRequest setLitterWeight(@Nullable BigDecimal litterWeight) {
        this.litterWeight = litterWeight;
        return this;
    }

    @Nullable
    public Long getAnimalId() {
        return animalId;
    }

    @Nullable
    public Integer getAnimalVersion() {
        return animalVersion;
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
}
