package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** 字典导航（ADM-D49）。本批返回 8 类，财务分类待模块10。 */
@Data
public class DictTypeVO {

    private String dictType;
    private String name;
    private String icon;
    private Integer itemCount;
    /** NONE / SPECIES / CATEGORY */
    private String scopeMode;
    /** 收支分类为 true（平台维护，用户不能改）；本批均为 false */
    private Boolean platformOnly;
}
