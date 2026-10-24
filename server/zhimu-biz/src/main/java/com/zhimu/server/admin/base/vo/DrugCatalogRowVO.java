package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ADM-D24 兽药疫苗目录列表行：含按畜种用量（列表“推荐剂量 / 休药期”列）与建档数。
 */
@Getter
@Setter
public class DrugCatalogRowVO {

    private Long id;
    private String drugCategory;
    private String drugCategoryText;
    private String name;
    private String alias;
    private String dosageForm;
    private String spec;
    private String measureUnit;
    private String measureUnitText;
    private String packUnit;
    private BigDecimal packSize;
    private String vaccineDoseUnit;
    private String vaccineDoseUnitText;
    private String defaultVaccinateMethodCode;
    private String defaultVaccinateMethodText;
    private String concentration;
    private List<DisinfectMethodVO> disinfectMethods = new ArrayList<>();
    private List<DrugUsageVO> usages = new ArrayList<>();
    private String status;
    private String statusText;
    private Boolean banned;
    /** 已建药品档案的养殖场数 */
    private Integer archiveFarmCount;
    private String lastChangedByName;
    private String lastChangedAt;
    private Integer sortNo;

    @Getter
    @Setter
    public static class DisinfectMethodVO {
        private String code;
        private String name;
    }
}
