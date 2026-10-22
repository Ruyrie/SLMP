package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记照蛋（API-B30）。
 */
public class CandlingRequest {

    @Nullable
    private String candleDate;
    @Nullable
    private Integer fertileCount;
    @Nullable
    private Integer version;

    public CandlingRequest setCandleDate(@Nullable String candleDate) {
        this.candleDate = candleDate;
        return this;
    }

    public CandlingRequest setFertileCount(@Nullable Integer fertileCount) {
        this.fertileCount = fertileCount;
        return this;
    }

    public CandlingRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getCandleDate() {
        return candleDate;
    }

    @Nullable
    public Integer getFertileCount() {
        return fertileCount;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
