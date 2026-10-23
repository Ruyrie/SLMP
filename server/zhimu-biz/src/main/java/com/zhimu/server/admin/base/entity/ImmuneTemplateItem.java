package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 免疫模板项目（t_immune_template_item）：按模板版本，已发布版本不再修改。
 * 只按日龄或只按日期二选一，由 schedule_type 决定。
 */
@Getter
@Setter
@TableName("t_immune_template_item")
public class ImmuneTemplateItem extends BaseEntity {

    private Long templateId;
    private Long versionId;
    private Long sourceItemId;
    /** ITEM_CHANGE：NONE / ADDED / MODIFIED / DELETED */
    private String changeType;
    private Integer sortNo;
    /** 疫苗目录 ID（t_drug_catalog） */
    private Long vaccineId;
    /** 免次，例如 首免、二免、加强 */
    private String doseRound;
    /** SCHEDULE_TYPE：BY_DAY_AGE / BY_DATE */
    private String scheduleType;
    private Integer dayAge;
    private Integer planMonth;
    private Integer planDay;
    private Integer advanceDays;
    private BigDecimal dose;
    /** VACCINE_DOSE_UNIT：DOSE_PER_HEAD / ML_PER_HEAD / DOSE_PER_BIRD */
    private String doseUnit;
    private String vaccinateMethodCode;
    private String remark;
}
