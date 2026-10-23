package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 登记下拉候选（API-B04）与 RegisterContextVO.animal：按 purpose 追加字段
 * （MATING：gestationDays、firstMatingDate、lastWeanDate；FARROWING：expectedDueDate、daysToDue；
 * WEANING：lastFarrowDate、liveBorn）。
 */
@Getter
@Setter
public class BreedingAnimalOptionVO {

    private long animalId;
    private String earTag;
    private String breedName;
    private int parity;
    private String status;
    private String statusText;
    private long penId;
    private String penName;
    private int version;
    private Integer gestationDays;
    private String firstMatingDate;
    private String lastWeanDate;
    private String expectedDueDate;
    private Integer daysToDue;
    private String lastFarrowDate;
    private Integer liveBorn;
}
