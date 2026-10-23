package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 待产列表项（API-B22）。 */
@Getter
@Setter
public class FarrowDueItemVO {

    private String group;
    private String groupText;
    private long penId;
    private String penName;
    private String remindBeforeDate;
    private String remindBeforeSentAt;
    private String remindDueSentAt;
    private String reminderText;
    private List<String> staffNames;
    /** 员工恒为 false */
    private boolean canRegister;
    private long animalId;
    private String earTag;
    private String breedName;
    private int parity;
    private String firstMatingDate;
    private String expectedDueDate;
    /** 可为负 */
    private int daysToDue;
    /** 员工恒为 false */
    private boolean canOpenArchive;
}
