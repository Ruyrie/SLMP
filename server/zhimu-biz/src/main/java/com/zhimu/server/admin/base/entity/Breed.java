package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 品种（t_breed）：品类内唯一，携带繁育参数与后代默认品种。 */
@Getter
@Setter
@TableName("t_breed")
public class Breed extends BaseAdminEntity {

    private Long categoryId;
    private String name;
    private String description;
    /** 妊娠期（天），非禽类；1–400 */
    private Integer gestationDays;
    /** 孵化期（天），禽类；1–400 */
    private Integer incubationDays;
    /** 配种后进入待妊检的天数，默认 21 */
    private Integer pregnancyCheckDays;
    private Long offspringBreedId;
    private Integer sortNo;
    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
    private String disableReason;
}
