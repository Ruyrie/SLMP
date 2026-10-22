package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记死亡 / 淘汰（API-B09）。
 */
public class AnimalExitRequest {

    @Nullable
    private String exitType;
    @Nullable
    private String exitDate;
    @Nullable
    private String reason;
    @Nullable
    private Integer version;

    public AnimalExitRequest setExitType(@Nullable String exitType) {
        this.exitType = exitType;
        return this;
    }

    public AnimalExitRequest setExitDate(@Nullable String exitDate) {
        this.exitDate = exitDate;
        return this;
    }

    public AnimalExitRequest setReason(@Nullable String reason) {
        this.reason = reason;
        return this;
    }

    public AnimalExitRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getExitType() {
        return exitType;
    }

    @Nullable
    public String getExitDate() {
        return exitDate;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
