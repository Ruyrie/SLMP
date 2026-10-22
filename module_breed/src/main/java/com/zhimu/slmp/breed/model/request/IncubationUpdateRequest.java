package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 修改入孵 / 照蛋 / 未转批次的出雏（API-B29）。
 */
public class IncubationUpdateRequest {

    @Nullable
    private String setDate;
    @Nullable
    private Integer setCount;
    @Nullable
    private String candleDate;
    @Nullable
    private String hatchDate;
    @Nullable
    private Integer fertileCount;
    @Nullable
    private Integer hatchedCount;
    @Nullable
    private Integer healthyCount;
    @Nullable
    private Integer version;

    public IncubationUpdateRequest setSetDate(@Nullable String setDate) {
        this.setDate = setDate;
        return this;
    }

    public IncubationUpdateRequest setSetCount(@Nullable Integer setCount) {
        this.setCount = setCount;
        return this;
    }

    public IncubationUpdateRequest setCandleDate(@Nullable String candleDate) {
        this.candleDate = candleDate;
        return this;
    }

    public IncubationUpdateRequest setHatchDate(@Nullable String hatchDate) {
        this.hatchDate = hatchDate;
        return this;
    }

    public IncubationUpdateRequest setFertileCount(@Nullable Integer fertileCount) {
        this.fertileCount = fertileCount;
        return this;
    }

    public IncubationUpdateRequest setHatchedCount(@Nullable Integer hatchedCount) {
        this.hatchedCount = hatchedCount;
        return this;
    }

    public IncubationUpdateRequest setHealthyCount(@Nullable Integer healthyCount) {
        this.healthyCount = healthyCount;
        return this;
    }

    public IncubationUpdateRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getSetDate() {
        return setDate;
    }

    @Nullable
    public Integer getSetCount() {
        return setCount;
    }

    @Nullable
    public String getCandleDate() {
        return candleDate;
    }

    @Nullable
    public String getHatchDate() {
        return hatchDate;
    }

    @Nullable
    public Integer getFertileCount() {
        return fertileCount;
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
    public Integer getVersion() {
        return version;
    }
}
