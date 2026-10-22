package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 种畜档案（API-B05、B07、B08、B09）：B06 头部、档案信息、繁育表现与当前周期。
 */
public class BreedingAnimalDetailVO {

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
    @Nullable
    private String animalLabel;
    @Nullable
    private String filingDate;
    /** ANIMAL_SOURCE */
    @Nullable
    private String source;
    @Nullable
    private String sourceText;
    private int initialParity;
    @Nullable
    private String birthDate;
    @Nullable
    private Stats stats;
    /** 空怀时为空 */
    @Nullable
    private CurrentCycle currentCycle;
    private boolean canEdit;
    private boolean canExit;

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

    @Nullable
    public String getAnimalLabel() {
        return animalLabel;
    }

    @Nullable
    public String getFilingDate() {
        return filingDate;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    @Nullable
    public String getSourceText() {
        return sourceText;
    }

    public int getInitialParity() {
        return initialParity;
    }

    @Nullable
    public String getBirthDate() {
        return birthDate;
    }

    @Nullable
    public Stats getStats() {
        return stats;
    }

    @Nullable
    public CurrentCycle getCurrentCycle() {
        return currentCycle;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public boolean isCanExit() {
        return canExit;
    }

    /** 繁育表现：无产仔不输出 avg_live_born */
    public static class Stats {

        private int farrowingCount;
        @Nullable
        private BigDecimal avgLiveBorn;
        @Nullable
        private String lastWeanDate;
        @Nullable
        private Integer lastWeanedCount;

        public int getFarrowingCount() {
            return farrowingCount;
        }

        @Nullable
        public BigDecimal getAvgLiveBorn() {
            return avgLiveBorn;
        }

        @Nullable
        public String getLastWeanDate() {
            return lastWeanDate;
        }

        @Nullable
        public Integer getLastWeanedCount() {
            return lastWeanedCount;
        }
    }

    /** 当前繁殖周期 */
    public static class CurrentCycle {

        private long cycleId;
        /** CYCLE_STATUS */
        @Nullable
        private String status;
        @Nullable
        private String firstMatingDate;
        private int matingCount;
        @Nullable
        private Integer daysSinceMating;
        @Nullable
        private String checkDueDate;
        @Nullable
        private Boolean pendingCheck;
        @Nullable
        private String expectedDueDate;
        @Nullable
        private Integer daysToDue;
        @Nullable
        private String farrowDate;
        @Nullable
        private Integer liveBorn;
        @Nullable
        private Integer nursingDays;

        public long getCycleId() {
            return cycleId;
        }

        @Nullable
        public String getStatus() {
            return status;
        }

        @Nullable
        public String getFirstMatingDate() {
            return firstMatingDate;
        }

        public int getMatingCount() {
            return matingCount;
        }

        @Nullable
        public Integer getDaysSinceMating() {
            return daysSinceMating;
        }

        @Nullable
        public String getCheckDueDate() {
            return checkDueDate;
        }

        @Nullable
        public Boolean getPendingCheck() {
            return pendingCheck;
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
        public String getFarrowDate() {
            return farrowDate;
        }

        @Nullable
        public Integer getLiveBorn() {
            return liveBorn;
        }

        @Nullable
        public Integer getNursingDays() {
            return nursingDays;
        }
    }
}
