package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记产仔（API-B17）。
 */
public class FarrowingRequest {

    @Nullable
    private Long animalId;
    @Nullable
    private Integer animalVersion;
    @Nullable
    private String farrowDate;
    @Nullable
    private Integer totalBorn;
    @Nullable
    private Integer liveBorn;
    @Nullable
    private Integer stillborn;
    @Nullable
    private Boolean transferToBatch;
    @Nullable
    private Long targetPenId;
    @Nullable
    private Long offspringBreedId;
    @Nullable
    private Boolean capacityConfirmed;

    public FarrowingRequest setAnimalId(@Nullable Long animalId) {
        this.animalId = animalId;
        return this;
    }

    public FarrowingRequest setAnimalVersion(@Nullable Integer animalVersion) {
        this.animalVersion = animalVersion;
        return this;
    }

    public FarrowingRequest setFarrowDate(@Nullable String farrowDate) {
        this.farrowDate = farrowDate;
        return this;
    }

    public FarrowingRequest setTotalBorn(@Nullable Integer totalBorn) {
        this.totalBorn = totalBorn;
        return this;
    }

    public FarrowingRequest setLiveBorn(@Nullable Integer liveBorn) {
        this.liveBorn = liveBorn;
        return this;
    }

    public FarrowingRequest setStillborn(@Nullable Integer stillborn) {
        this.stillborn = stillborn;
        return this;
    }

    public FarrowingRequest setTransferToBatch(@Nullable Boolean transferToBatch) {
        this.transferToBatch = transferToBatch;
        return this;
    }

    public FarrowingRequest setTargetPenId(@Nullable Long targetPenId) {
        this.targetPenId = targetPenId;
        return this;
    }

    public FarrowingRequest setOffspringBreedId(@Nullable Long offspringBreedId) {
        this.offspringBreedId = offspringBreedId;
        return this;
    }

    public FarrowingRequest setCapacityConfirmed(@Nullable Boolean capacityConfirmed) {
        this.capacityConfirmed = capacityConfirmed;
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
    public String getFarrowDate() {
        return farrowDate;
    }

    @Nullable
    public Integer getTotalBorn() {
        return totalBorn;
    }

    @Nullable
    public Integer getLiveBorn() {
        return liveBorn;
    }

    @Nullable
    public Integer getStillborn() {
        return stillborn;
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
}
