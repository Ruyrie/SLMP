package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 待出雏列表项（API-B23）。 */
@Getter
@Setter
public class HatchDueItemVO {

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
    private long incubationId;
    private long breedingBatchId;
    /** 栏舍名 · 批次号 */
    private String flockText;
    private String setDate;
    private int setCount;
    /** 未照蛋不输出 */
    private Integer fertileCount;
    private String expectedHatchDate;
    private int daysToHatch;
    /** 员工恒为 false */
    private boolean canOpen;
}
