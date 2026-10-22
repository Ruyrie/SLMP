package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-L03 存栏汇总（L03 汇总卡）。
 */
public class StockSummaryVO {

    private int totalStock;
    private int activeBatchCount;
    private int withdrawalBatchCount;
    private int endedBatchCount;
    private int entryTotal;
    private int deathTotal;
    private int capacityTotal;
    private int commercialPenCount;
    private BigDecimal mortalityRate;
    private BigDecimal occupancyRate;
    private String unit;
    private String unitText;
    private List<CategoryShareVO> categories;

    public int getTotalStock() {
        return totalStock;
    }

    public int getActiveBatchCount() {
        return activeBatchCount;
    }

    public int getWithdrawalBatchCount() {
        return withdrawalBatchCount;
    }

    public int getEndedBatchCount() {
        return endedBatchCount;
    }

    public int getEntryTotal() {
        return entryTotal;
    }

    public int getDeathTotal() {
        return deathTotal;
    }

    public int getCapacityTotal() {
        return capacityTotal;
    }

    public int getCommercialPenCount() {
        return commercialPenCount;
    }

    /** 入栏和为 0 时为空，显示“无数据” */
    @Nullable
    public BigDecimal getMortalityRate() {
        return mortalityRate;
    }

    @Nullable
    public BigDecimal getOccupancyRate() {
        return occupancyRate;
    }

    public String getUnit() {
        return unit;
    }

    @Nullable
    public String getUnitText() {
        return unitText;
    }

    public List<CategoryShareVO> getCategories() {
        return categories == null ? Collections.emptyList() : categories;
    }

    /** 品类占比 */
    public static class CategoryShareVO {
        private long categoryId;
        private String categoryName;
        private int stock;
        private int batchCount;
        private List<StageGroupItem> stageGroups;

        public long getCategoryId() {
            return categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public int getStock() {
            return stock;
        }

        public int getBatchCount() {
            return batchCount;
        }

        public List<StageGroupItem> getStageGroups() {
            return stageGroups == null ? Collections.emptyList() : stageGroups;
        }
    }

    /** 品类内按生长阶段的存栏 */
    public static class StageGroupItem {
        private String stageShortName;
        private int stock;

        public String getStageShortName() {
            return stageShortName;
        }

        public int getStock() {
            return stock;
        }
    }
}
