package com.zhimu.server.admin.system.query;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** ADM-S07 分布地图：level 默认 PROVINCE；CITY/DISTRICT/POINT 需 parent_code。 */
@Getter
@Setter
public class FarmMapQuery {

    @Pattern(regexp = "PROVINCE|CITY|DISTRICT|POINT", message = "地图层级不正确")
    private String level;

    private String parentCode;

    /** 畜种（SPECIES）：PIG / POULTRY / CATTLE / SHEEP，选填 */
    @Pattern(regexp = "PIG|POULTRY|CATTLE|SHEEP", message = "品类分组不正确")
    private String categoryGroup;

    /** 主要品类编号（t_category.id），选填；与 category_group 同时传时取交集。 */
    @Min(value = 1, message = "品类编号不正确")
    private Long categoryId;

    public String levelOrDefault() {
        return level == null || level.isBlank() ? "PROVINCE" : level;
    }
}
