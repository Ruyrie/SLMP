package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 登记妊检（API-B15）；check_method 不传，服务端默认 ULTRASOUND。
 */
public class PregnancyCheckRequest {

    @Nullable
    private Long animalId;
    @Nullable
    private Integer animalVersion;
    @Nullable
    private Long cycleId;
    @Nullable
    private String checkDate;
    @Nullable
    private String result;
    @Nullable
    private String checkMethod;

    public PregnancyCheckRequest setAnimalId(@Nullable Long animalId) {
        this.animalId = animalId;
        return this;
    }

    public PregnancyCheckRequest setAnimalVersion(@Nullable Integer animalVersion) {
        this.animalVersion = animalVersion;
        return this;
    }

    public PregnancyCheckRequest setCycleId(@Nullable Long cycleId) {
        this.cycleId = cycleId;
        return this;
    }

    public PregnancyCheckRequest setCheckDate(@Nullable String checkDate) {
        this.checkDate = checkDate;
        return this;
    }

    public PregnancyCheckRequest setResult(@Nullable String result) {
        this.result = result;
        return this;
    }

    public PregnancyCheckRequest setCheckMethod(@Nullable String checkMethod) {
        this.checkMethod = checkMethod;
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
    public Long getCycleId() {
        return cycleId;
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
}
