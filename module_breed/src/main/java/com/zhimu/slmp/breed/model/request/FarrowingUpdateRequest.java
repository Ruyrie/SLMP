package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 修改未转批次的产仔（API-B18）。
 */
public class FarrowingUpdateRequest {

    @Nullable
    private String farrowDate;
    @Nullable
    private Integer totalBorn;
    @Nullable
    private Integer liveBorn;
    @Nullable
    private Integer stillborn;
    @Nullable
    private Integer version;

    public FarrowingUpdateRequest setFarrowDate(@Nullable String farrowDate) {
        this.farrowDate = farrowDate;
        return this;
    }

    public FarrowingUpdateRequest setTotalBorn(@Nullable Integer totalBorn) {
        this.totalBorn = totalBorn;
        return this;
    }

    public FarrowingUpdateRequest setLiveBorn(@Nullable Integer liveBorn) {
        this.liveBorn = liveBorn;
        return this;
    }

    public FarrowingUpdateRequest setStillborn(@Nullable Integer stillborn) {
        this.stillborn = stillborn;
        return this;
    }

    public FarrowingUpdateRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
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
    public Integer getVersion() {
        return version;
    }
}
