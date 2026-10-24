package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 免疫模板项目。schedule_type = BY_DAY_AGE 时 day_age 有值；BY_DATE 时 plan_month / plan_day / advance_days 有值。
 * before 仅 MODIFIED 项返回线上值。
 */
@Getter
@Setter
public class ImmuneItemVO {

    private Long itemId;
    private Integer sortNo;
    private Long vaccineId;
    private String vaccineName;
    private String doseRound;
    private String scheduleType;
    private String scheduleTypeText;
    private Integer dayAge;
    private Integer planMonth;
    private Integer planDay;
    private Integer advanceDays;
    private BigDecimal dose;
    private String doseUnit;
    private String doseUnitText;
    private String vaccinateMethodCode;
    private String vaccinateMethodText;
    private String remark;
    /** ITEM_CHANGE：NONE / ADDED / MODIFIED / DELETED */
    private String changeType;
    private ImmuneItemVO before;
}
