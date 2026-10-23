package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/** 种畜列表项（API-B02）。 */
@Getter
@Setter
public class BreedingAnimalItemVO {

    private long animalId;
    private long breedId;
    private long penId;
    private String earTag;
    private String breedName;
    private String penName;
    private String status;
    private String statusText;
    /** 当前胎次（已产胎数） */
    private int parity;
    /** 登记时作为 animal_version 提交 */
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
}
