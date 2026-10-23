package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/** 待妊检（API-B14）。 */
@Getter
@Setter
public class PendingCheckVO {

    private long animalId;
    private long cycleId;
    private int animalVersion;
    private int parity;
    private int daysSinceMating;
    private String earTag;
    private String breedName;
    private String firstMatingDate;
    private String expectedDueDate;
}
