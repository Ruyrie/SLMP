package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-D20 可选兽药（只列未禁用、本场有可用结存的兽药档案；用量与休药期按畜种取）。
 */
public class MedicationDrugOptionVO {

    private long drugId;
    private long catalogId;
    private int withdrawalDays;
    @Nullable
    private BigDecimal availableQty;
    /** 适用该畜种；不适用时须写用药说明 */
    private boolean applicable;
    @Nullable
    private String drugName;
    @Nullable
    private String spec;
    @Nullable
    private String stockUnit;
    @Nullable
    private String hintText;
    @Nullable
    private String routeCode;
    @Nullable
    private String routeName;
    @Nullable
    private String doseUnit;
    @Nullable
    private BigDecimal recommendedDose;
    @Nullable
    private Integer recommendedCourseDays;

    public long getDrugId() {
        return drugId;
    }

    public long getCatalogId() {
        return catalogId;
    }

    public int getWithdrawalDays() {
        return withdrawalDays;
    }

    @Nullable
    public BigDecimal getAvailableQty() {
        return availableQty;
    }

    public boolean isApplicable() {
        return applicable;
    }

    @Nullable
    public String getDrugName() {
        return drugName;
    }

    @Nullable
    public String getSpec() {
        return spec;
    }

    @Nullable
    public String getStockUnit() {
        return stockUnit;
    }

    @Nullable
    public String getHintText() {
        return hintText;
    }

    @Nullable
    public String getRouteCode() {
        return routeCode;
    }

    @Nullable
    public String getRouteName() {
        return routeName;
    }

    @Nullable
    public String getDoseUnit() {
        return doseUnit;
    }

    @Nullable
    public BigDecimal getRecommendedDose() {
        return recommendedDose;
    }

    @Nullable
    public Integer getRecommendedCourseDays() {
        return recommendedCourseDays;
    }

    /** API-D20 data：list 与 banned_hint（关键词命中禁用药时的提示） */
    public static class ListResult {

        @Nullable
        private List<MedicationDrugOptionVO> list;
        @Nullable
        private String bannedHint;

        @NonNull
        public List<MedicationDrugOptionVO> getList() {
            return list == null ? Collections.emptyList() : list;
        }

        @Nullable
        public String getBannedHint() {
            return bannedHint;
        }
    }
}
