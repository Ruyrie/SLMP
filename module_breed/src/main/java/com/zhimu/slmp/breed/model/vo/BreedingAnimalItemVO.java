package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 种畜列表项（API-B02，B01 母猪列表）。
 */
public class BreedingAnimalItemVO {

    private long animalId;
    private long breedId;
    private long penId;
    @Nullable
    private String earTag;
    @Nullable
    private String breedName;
    @Nullable
    private String penName;
    /** BREEDING_STATUS */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    /** 当前胎次（已产胎数） */
    private int parity;
    /** 登记时作为 animal_version 提交 */
    private int version;
    @Nullable
    private String firstMatingDate;
    @Nullable
    private Integer daysSinceMating;
    @Nullable
    private Boolean pendingCheck;
    @Nullable
    private Integer nursingDays;
    @Nullable
    private String expectedDueDate;
    @Nullable
    private Integer daysToDue;
    @Nullable
    private String lastFarrowDate;
    @Nullable
    private Integer liveBorn;
    @Nullable
    private String lastWeanDate;
    /** NEXT_ACTION */
    @Nullable
    private String nextAction;
    @Nullable
    private String nextActionText;
    @Nullable
    private String exitType;
    @Nullable
    private String exitTypeText;
    @Nullable
    private String exitDate;
    @Nullable
    private String exitReason;

    public long getAnimalId() {
        return animalId;
    }

    public long getBreedId() {
        return breedId;
    }

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getEarTag() {
        return earTag;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    public int getParity() {
        return parity;
    }

    public int getVersion() {
        return version;
    }

    @Nullable
    public String getFirstMatingDate() {
        return firstMatingDate;
    }

    @Nullable
    public Integer getDaysSinceMating() {
        return daysSinceMating;
    }

    @Nullable
    public Boolean getPendingCheck() {
        return pendingCheck;
    }

    @Nullable
    public Integer getNursingDays() {
        return nursingDays;
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

    @Nullable
    public String getLastWeanDate() {
        return lastWeanDate;
    }

    @Nullable
    public String getNextAction() {
        return nextAction;
    }

    @Nullable
    public String getNextActionText() {
        return nextActionText;
    }

    @Nullable
    public String getExitType() {
        return exitType;
    }

    @Nullable
    public String getExitTypeText() {
        return exitTypeText;
    }

    @Nullable
    public String getExitDate() {
        return exitDate;
    }

    @Nullable
    public String getExitReason() {
        return exitReason;
    }
}
