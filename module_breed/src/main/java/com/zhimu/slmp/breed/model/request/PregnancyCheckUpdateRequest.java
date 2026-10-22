package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 修改妊检（API-B16）。
 */
public class PregnancyCheckUpdateRequest {

    @Nullable
    private String checkDate;
    @Nullable
    private String result;
    @Nullable
    private String checkMethod;
    @Nullable
    private Integer version;

    public PregnancyCheckUpdateRequest setCheckDate(@Nullable String checkDate) {
        this.checkDate = checkDate;
        return this;
    }

    public PregnancyCheckUpdateRequest setResult(@Nullable String result) {
        this.result = result;
        return this;
    }

    public PregnancyCheckUpdateRequest setCheckMethod(@Nullable String checkMethod) {
        this.checkMethod = checkMethod;
        return this;
    }

    public PregnancyCheckUpdateRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getCheckDate() {
        return checkDate;
    }

    @Nullable
    public String getResult() {
        return result;
    }

    @Nullable
    public String getCheckMethod() {
        return checkMethod;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
