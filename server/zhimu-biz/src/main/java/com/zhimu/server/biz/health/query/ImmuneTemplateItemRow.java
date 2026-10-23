package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 免疫模板项目（t_immune_template_item）：生成免疫计划用。 */
@Getter
@Setter
public class ImmuneTemplateItemRow {
    private Long id;
    private Long templateId;
    private Long versionId;
    private Integer sortNo;
    private Long vaccineId;
    private String doseRound;
    /** BY_DAY_AGE / BY_DATE */
    private String scheduleType;
    private Integer dayAge;
    private Integer planMonth;
    private Integer planDay;
    private Integer advanceDays;
    private BigDecimal dose;
    private String doseUnit;
    private String vaccinateMethodCode;
}
