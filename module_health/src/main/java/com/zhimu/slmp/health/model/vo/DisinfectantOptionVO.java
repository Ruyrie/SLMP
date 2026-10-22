package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D35 消毒剂下拉与库存提示：stock_hint_type 为 NORMAL / NEAR_EXPIRY / NO_STOCK。
 * stock_unit 为客户端补充的可空字段（用量单位，缺失时按“升”）。
 */
public class DisinfectantOptionVO {

    private long catalogId;
    @Nullable
    private Long drugId;
    @Nullable
    private String name;
    @Nullable
    private String recommendedConcentration;
    @Nullable
    private String stockHintType;
    @Nullable
    private String stockHintText;
    @Nullable
    private List<String> methodCodes;
    @Nullable
    private String stockUnit;

    public long getCatalogId() {
        return catalogId;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getRecommendedConcentration() {
        return recommendedConcentration;
    }

    @Nullable
    public String getStockHintType() {
        return stockHintType;
    }

    @Nullable
    public String getStockHintText() {
        return stockHintText;
    }

    @NonNull
    public List<String> getMethodCodes() {
        return methodCodes == null ? Collections.emptyList() : methodCodes;
    }

    @Nullable
    public String getStockUnit() {
        return stockUnit;
    }
}
