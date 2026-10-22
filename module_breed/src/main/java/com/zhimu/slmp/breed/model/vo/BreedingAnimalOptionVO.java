package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 登记下拉候选（API-B04）与 RegisterContextVO.animal：按 purpose 追加字段
 * （MATING：gestation_days、first_mating_date、last_wean_date；FARROWING：expected_due_date、days_to_due；
 * WEANING：last_farrow_date、live_born）。
 */
public class BreedingAnimalOptionVO {

    private long animalId;
    @Nullable
    private String earTag;
    @Nullable
    private String breedName;
    private int parity;
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    private long penId;
    @Nullable
    private String penName;
    private int version;
    @Nullable
    private Integer gestationDays;
    @Nullable
    private String firstMatingDate;
    @Nullable
    private String lastWeanDate;
    @Nullable
    private String expectedDueDate;
    @Nullable
    private Integer daysToDue;
    @Nullable
    private String lastFarrowDate;
    @Nullable
    private Integer liveBorn;

    public long getAnimalId() {
        return animalId;
    }

    @Nullable
    public String getEarTag() {
        return earTag;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public int getParity() {
        return parity;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    public int getVersion() {
        return version;
    }

    @Nullable
    public Integer getGestationDays() {
        return gestationDays;
    }

    @Nullable
    public String getFirstMatingDate() {
        return firstMatingDate;
    }

    @Nullable
    public String getLastWeanDate() {
        return lastWeanDate;
    }

    @Nullable
    public String getExpectedDueDate() {
        return expectedDueDate;
    }

    @Nullable
    public Integer getDaysToDue() {
        return daysToDue;
    }

    @Nullable
    public String getLastFarrowDate() {
        return lastFarrowDate;
    }

    @Nullable
    public Integer getLiveBorn() {
        return liveBorn;
    }
}
