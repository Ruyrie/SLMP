package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * API-E14 可出栏批次与休药状态（E06、E12）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleBatchOptionVO {

    private long batchId;
    private String batchNo;
    private String penName;
    private long categoryId;
    private String categoryName;
    @Nullable
    private String breedName;
    private String countUnit;
    private String countUnitText;
    private int dayAge;
    private int currentCount;
    private String entryDate;
    private boolean inWithdrawal;
    private List<PenStock> pens;
    @Nullable
    private String sellableDate;
    @Nullable
    private Integer withdrawalDaysLeft;
    @Nullable
    private String withdrawalDrugName;

    public long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getPenName() {
        return penName;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public String getCountUnit() {
        return countUnit;
    }

    public String getCountUnitText() {
        return countUnitText;
    }

    public int getDayAge() {
        return dayAge;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public String getEntryDate() {
        return entryDate;
    }

    public boolean isInWithdrawal() {
        return inWithdrawal;
    }

    @NonNull
    public List<PenStock> getPens() {
        return pens == null ? java.util.Collections.emptyList() : pens;
    }

    @Nullable
    public String getSellableDate() {
        return sellableDate;
    }

    @Nullable
    public Integer getWithdrawalDaysLeft() {
        return withdrawalDaysLeft;
    }

    @Nullable
    public String getWithdrawalDrugName() {
        return withdrawalDrugName;
    }

    /** 批次所在栏舍及在栏数 */
    public static class PenStock {

        private long penId;
        private String penName;
        private int stockCount;

        public long getPenId() {
            return penId;
        }

        public String getPenName() {
            return penName;
        }

        public int getStockCount() {
            return stockCount;
        }
    }
}
