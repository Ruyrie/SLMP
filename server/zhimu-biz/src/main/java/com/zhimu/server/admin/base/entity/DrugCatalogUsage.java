package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 兽药疫苗目录按畜种用量与休药期（t_drug_catalog_usage）：兽药、疫苗每畜种一行，消毒剂不设。 */
@Getter
@Setter
@TableName("t_drug_catalog_usage")
public class DrugCatalogUsage extends BaseEntity {

    private Long catalogId;
    /** SPECIES：PIG / POULTRY / CATTLE / SHEEP */
    private String species;
    /** 适用于该畜种：1 是 */
    private Boolean applicable;
    /** ADMIN_ROUTE：MIX_FEED / MIX_WATER / IM / SC / IV / ORAL / EXTERNAL */
    private String adminRoute;
    private BigDecimal recommendedDose;
    /** DRUG_DOSE_UNIT 或 VACCINE_DOSE_UNIT（疫苗） */
    private String doseUnit;
    private Integer courseDays;
    private Integer withdrawalDays;
}
