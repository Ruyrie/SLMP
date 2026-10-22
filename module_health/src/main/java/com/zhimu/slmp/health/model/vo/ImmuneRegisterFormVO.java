package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D12 登记接种预填：疫苗、默认批号（先到期先出）、剂量、数量、日期区间、登记人。
 */
public class ImmuneRegisterFormVO {

    @Nullable
    private ImmunePlanOptionVO plan;
    @Nullable
    private String vaccineName;
    @Nullable
    private String doseUnit;
    @Nullable
    private String methodCode;
    @Nullable
    private String creatorName;
    @Nullable
    private String dateDefault;
    @Nullable
    private String dateMin;
    @Nullable
    private String dateMax;
    @Nullable
    private BigDecimal templateDose;
    @Nullable
    private BigDecimal dose;
    private int quantityDefault;
    private int currentCount;
    private boolean backfill;
    @Nullable
    private Long drugId;
    /** 员工不输出 */
    @Nullable
    private Long defaultExecutorId;
    @Nullable
    private DrugLotOptionVO defaultLot;
    /** 没有可用批号时的提示（按角色） */
    @Nullable
    private String noLotText;

    @Nullable
    public ImmunePlanOptionVO getPlan() {
        return plan;
    }

    @Nullable
    public String getVaccineName() {
        return vaccineName;
    }

    @Nullable
    public String getDoseUnit() {
        return doseUnit;
    }

    @Nullable
    public String getMethodCode() {
        return methodCode;
    }

    @Nullable
    public String getCreatorName() {
        return creatorName;
    }

    @Nullable
    public String getDateDefault() {
        return dateDefault;
    }

    @Nullable
    public String getDateMin() {
        return dateMin;
    }

    @Nullable
    public String getDateMax() {
        return dateMax;
    }

    @Nullable
    public BigDecimal getTemplateDose() {
        return templateDose;
    }

    @Nullable
    public BigDecimal getDose() {
        return dose;
    }

    public int getQuantityDefault() {
        return quantityDefault;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public boolean isBackfill() {
        return backfill;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public Long getDefaultExecutorId() {
        return defaultExecutorId;
    }

    @Nullable
    public DrugLotOptionVO getDefaultLot() {
        return defaultLot;
    }

    @Nullable
    public String getNoLotText() {
        return noLotText;
    }
}
