package com.zhimu.server.admin.base.vo;

import lombok.Data;

/**
 * API-U09 breeds[]，字段与 App CategoryBreedVO 完全一致（键为 has_std_weight / has_feed_std / has_immune_template）。
 * 值为 null 的字段不输出：例如杜长大没有 gestation_days。
 */
@Data
public class BaseCategoryBreedVO {

    private Long id;
    private String name;
    private Integer gestationDays;
    private Integer incubationDays;
    private Integer pregnancyCheckDays;
    private Long offspringBreedId;
    private Boolean hasStdWeight;
    private Boolean hasFeedStd;
    private Boolean hasImmuneTemplate;
}
