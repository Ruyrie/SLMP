package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-D25 / D26 / D27 药品详情与影响。 */
@Getter
@Setter
public class DrugCatalogDetailVO {

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
    private List<DrugCatalogRowVO.DisinfectMethodVO> disinfectMethods = new ArrayList<>();
    private List<DrugUsageVO> usages = new ArrayList<>();
    private String status;
    private String statusText;
    private String disableReason;
    private Boolean banned;
    /** 已建档养殖场数 */
    private Integer archiveFarmCount;
    private String lastChangeNote;
    private String lastChangedByName;
    private LocalDateTime lastChangedAt;
    /** 用过本品的在养批次数 */
    private Integer activeBatchUsedCount;
    /** ADM-D27 修改休药期后重算的批次数；未改动休药期时为空 */
    private Integer recalculatedBatchCount;
    private Integer version;
    private Integer sortNo;
}
