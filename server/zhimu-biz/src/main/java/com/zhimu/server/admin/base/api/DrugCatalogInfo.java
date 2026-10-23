package com.zhimu.server.admin.base.api;

import java.math.BigDecimal;

/**
 * 兽药疫苗目录简要信息（跨模块只读）：模块08 生成免疫计划、模块09 建档与入库校验使用。
 * measureUnit 为记账单位代码 G / ML / L / DOSE / KG。
 */
public record DrugCatalogInfo(Long id, String drugCategory, String name, String alias, String dosageForm,
                              String spec, String measureUnit, String packUnit, BigDecimal packSize,
                              String vaccineDoseUnit, String defaultVaccinateMethodCode, String status,
                              boolean banned, Long bannedDrugId) {

    public boolean enabled() {
        return "ENABLED".equals(status);
    }
}
