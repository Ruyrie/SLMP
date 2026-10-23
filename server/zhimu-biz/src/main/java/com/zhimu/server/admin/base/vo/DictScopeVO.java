package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** 字典项适用范围（ADM-D50–D54）。 */
@Data
public class DictScopeVO {

    /** DICT_SCOPE：SPECIES / CATEGORY */
    private String scopeType;
    private String species;
    private Long categoryId;
    private String categoryName;
}
