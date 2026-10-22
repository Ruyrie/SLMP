package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D13 本场疫苗档案下拉（含无库存，no_stock 时显示“暂无库存”）。
 */
public class VaccineOptionVO {

    private long drugId;
    private long catalogId;
    @Nullable
    private BigDecimal availableQty;
    private boolean noStock;
    @Nullable
    private String name;
    @Nullable
    private String doseUnit;
    /** 记账单位 */
    @Nullable
    private String stockUnit;
    @Nullable
    private String defaultMethodCode;

    public long getDrugId() {
        return drugId;
    }

    public long getCatalogId() {
        return catalogId;
    }

    @Nullable
    public BigDecimal getAvailableQty() {
        return availableQty;
    }

    public boolean isNoStock() {
        return noStock;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getDoseUnit() {
        return doseUnit;
    }

    @Nullable
    public String getStockUnit() {
        return stockUnit;
    }

    @Nullable
    public String getDefaultMethodCode() {
        return defaultMethodCode;
    }
}
