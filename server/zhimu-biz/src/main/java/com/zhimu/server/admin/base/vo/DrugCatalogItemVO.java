package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** API-U11 兽药疫苗目录搜索项（字段与 App module_stock CatalogResultVO.Item 一致）。 */
@Getter
@Setter
public class DrugCatalogItemVO {

    private Long id;
    private String name;
    private String alias;
    /** DRUG_CATEGORY：VET_DRUG / VACCINE / DISINFECTANT */
    private String drugCategory;
    private String spec;
    /** 记账单位代码：G / ML / L / DOSE / KG */
    private String measureUnit;
    private String packUnit;
    private BigDecimal packSize;
}
