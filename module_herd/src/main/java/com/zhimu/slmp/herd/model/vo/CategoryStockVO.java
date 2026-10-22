package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-L05 按品类存栏（L03 品类视图）；batches 只含在养批次。
 */
public class CategoryStockVO {

    private long categoryId;
    private String categoryName;
    private boolean isBreeding;
    private int stock;
    private int batchCount;
    private BigDecimal mortalityRate;
    private List<String> penNames;
    private List<BatchListItemVO> batches;
    private String unitText;

    public long getCategoryId() { return categoryId; }

    public String getCategoryName() { return categoryName; }

    public boolean isBreeding() { return isBreeding; }

    public int getStock() { return stock; }

    public int getBatchCount() { return batchCount; }

    @Nullable
    public BigDecimal getMortalityRate() { return mortalityRate; }

    public List<String> getPenNames() { return penNames == null ? Collections.emptyList() : penNames; }

    public List<BatchListItemVO> getBatches() { return batches == null ? Collections.emptyList() : batches; }

    @Nullable
    public String getUnitText() { return unitText; }
}
