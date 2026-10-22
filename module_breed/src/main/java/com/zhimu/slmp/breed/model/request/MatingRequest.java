package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记配种（API-B12）。
 */
public class MatingRequest {

    @Nullable
    private Long animalId;
    @Nullable
    private Integer animalVersion;
    @Nullable
    private String matingDate;
    @Nullable
    private String matingMethod;
    @Nullable
    private String boarInfo;

    public MatingRequest setAnimalId(@Nullable Long animalId) {
        this.animalId = animalId;
        return this;
    }

    public MatingRequest setAnimalVersion(@Nullable Integer animalVersion) {
        this.animalVersion = animalVersion;
        return this;
    }

    public MatingRequest setMatingDate(@Nullable String matingDate) {
        this.matingDate = matingDate;
        return this;
    }

    public MatingRequest setMatingMethod(@Nullable String matingMethod) {
        this.matingMethod = matingMethod;
        return this;
    }

    public MatingRequest setBoarInfo(@Nullable String boarInfo) {
        this.boarInfo = boarInfo;
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
    public String getMatingDate() {
        return matingDate;
    }

    @Nullable
    public String getMatingMethod() {
        return matingMethod;
    }

    @Nullable
    public String getBoarInfo() {
        return boarInfo;
    }
}
