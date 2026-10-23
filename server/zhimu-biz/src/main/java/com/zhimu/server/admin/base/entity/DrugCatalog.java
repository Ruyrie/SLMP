package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 兽药疫苗目录（t_drug_catalog）：平台维护；U11 只读搜索，模块09 建档时带出默认包装。
 * measure_unit 存代码 G / ML / L / DOSE / KG。
 */
@Getter
@Setter
@TableName("t_drug_catalog")
public class DrugCatalog extends BaseAdminEntity {

    /** DRUG_CATEGORY：VET_DRUG / VACCINE / DISINFECTANT */
    private String drugCategory;
    private String name;
    /** 别名，顿号分隔，搜索时包含匹配 */
    private String alias;
    private String dosageForm;
    private String spec;
    /** 记账单位代码：G / ML / L / DOSE / KG */
    private String measureUnit;
    private String packUnit;
    private BigDecimal packSize;
    /** VACCINE_DOSE_UNIT，疫苗必填 */
    private String vaccineDoseUnit;
    private String defaultVaccinateMethodCode;
    private String concentration;
    private String disinfectMethodCodes;
    /** BASE_STATUS：ENABLED / DISABLED */
    private String status;
    private String disableReason;
    /** 已列入禁用药：1 是 */
    private Boolean banned;
    private Long bannedDrugId;
    private String lastChangeNote;
}
