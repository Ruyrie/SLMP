package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** 品种（ADM-D05、D06 返回）。 */
@Data
public class BreedVO {

    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Integer gestationDays;
    private Integer incubationDays;
    private Integer pregnancyCheckDays;
    private Long offspringBreedId;
    private String status;
    private String statusText;
    private String disableReason;
    private Integer sortNo;
    private Integer version;
}
