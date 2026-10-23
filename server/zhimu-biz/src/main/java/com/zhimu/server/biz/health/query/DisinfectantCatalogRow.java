package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** 消毒剂目录（t_drug_catalog，drug_category = DISINFECTANT）：推荐浓度与适用消毒方式。 */
@Getter
@Setter
public class DisinfectantCatalogRow {
    private Long id;
    private String name;
    private String concentration;
    /** 逗号分隔的 DISINFECT_METHOD code */
    private String methodCodes;
    private String measureUnit;
}
