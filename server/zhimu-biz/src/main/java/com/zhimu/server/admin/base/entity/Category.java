package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 品类（t_category）：隶属畜种，决定计数单位 count_unit。 */
@Getter
@Setter
@TableName("t_category")
public class Category extends BaseAdminEntity {

    private String name;
    /** SPECIES：PIG / POULTRY / CATTLE / SHEEP */
    private String species;
    /** COUNT_UNIT：HEAD / BIRD */
    private String countUnit;
    /** 种畜品类，只设繁育参数与免疫模板 */
    private Boolean isBreeding;
    private Integer sortNo;
    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
    private String disableReason;
}
