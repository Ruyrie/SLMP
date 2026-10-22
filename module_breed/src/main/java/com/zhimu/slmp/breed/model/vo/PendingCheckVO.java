package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 待妊检（API-B14，B03 待妊检列表）。
 */
public class PendingCheckVO {

    private long animalId;
    private long cycleId;
    private int animalVersion;
    private int parity;
    private int daysSinceMating;
    @Nullable
    private String earTag;
    @Nullable
    private String breedName;
    @Nullable
    private String firstMatingDate;
    @Nullable
    private String expectedDueDate;

    public long getAnimalId() {
        return animalId;
    }

    public long getCycleId() {
        return cycleId;
    }

    public int getAnimalVersion() {
        return animalVersion;
    }

    public int getParity() {
        return parity;
    }

    public int getDaysSinceMating() {
        return daysSinceMating;
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
    public String getFirstMatingDate() {
        return firstMatingDate;
    }

    @Nullable
    public String getExpectedDueDate() {
        return expectedDueDate;
    }
}
