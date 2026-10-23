package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 种畜档案（API-B05、B07、B08、B09）。 */
@Getter
@Setter
public class BreedingAnimalDetailVO {

    private long animalId;
    private long breedId;
    private long penId;
    private String earTag;
    private String breedName;
    private String penName;
    private String status;
    private String statusText;
    private int parity;
    private int version;
    private String firstMatingDate;
    private Integer daysSinceMating;
    private Boolean pendingCheck;
    private Integer nursingDays;
    private String expectedDueDate;
    private Integer daysToDue;
    private String lastFarrowDate;
    private Integer liveBorn;
    private String lastWeanDate;
    private String nextAction;
    private String nextActionText;
    private String exitType;
    private String exitTypeText;
    private String exitDate;
    private String exitReason;
    private String animalLabel;
    private String filingDate;
    private String source;
    private String sourceText;
    private int initialParity;
    private String birthDate;
    private Stats stats;
    /** 空怀时为空 */
    private CurrentCycle currentCycle;
    private boolean canEdit;
    private boolean canExit;

    /** 繁育表现：无产仔不输出 avg_live_born */
    @Getter
    @Setter
    public static class Stats {
        private int farrowingCount;
        private BigDecimal avgLiveBorn;
        private String lastWeanDate;
        private Integer lastWeanedCount;
    }

    /** 当前繁殖周期 */
    @Getter
    @Setter
    public static class CurrentCycle {
        private long cycleId;
        private String status;
        private String firstMatingDate;
        private int matingCount;
        private Integer daysSinceMating;
        private String checkDueDate;
        private Boolean pendingCheck;
        private String expectedDueDate;
        private Integer daysToDue;
        private String farrowDate;
        private Integer liveBorn;
        private Integer nursingDays;
    }
}
