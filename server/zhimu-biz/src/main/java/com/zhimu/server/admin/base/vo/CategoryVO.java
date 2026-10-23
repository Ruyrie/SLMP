package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** 品类（ADM-D02、D03 返回；ADM-D04 内嵌 category）。 */
@Data
public class CategoryVO {

    private Long id;
    private String name;
    /** SPECIES：PIG / POULTRY / CATTLE / SHEEP */
    private String species;
    private String speciesText;
    /** COUNT_UNIT：HEAD / BIRD */
    private String countUnit;
    private String countUnitText;
    private Boolean isBreeding;
    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
    private String statusText;
    private String disableReason;
    private Integer sortNo;
    private Integer breedCount;
    private Integer version;
}
