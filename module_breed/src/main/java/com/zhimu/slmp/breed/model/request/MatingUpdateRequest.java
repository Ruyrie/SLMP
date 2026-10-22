package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 修改配种（API-B13）。
 */
public class MatingUpdateRequest {

    @Nullable
    private String matingDate;
    @Nullable
    private String matingMethod;
    @Nullable
    private String boarInfo;
    @Nullable
    private Integer version;

    public MatingUpdateRequest setMatingDate(@Nullable String matingDate) {
        this.matingDate = matingDate;
        return this;
    }

    public MatingUpdateRequest setMatingMethod(@Nullable String matingMethod) {
        this.matingMethod = matingMethod;
        return this;
    }

    public MatingUpdateRequest setBoarInfo(@Nullable String boarInfo) {
        this.boarInfo = boarInfo;
        return this;
    }

    public MatingUpdateRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
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

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
