package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 种禽群（API-L06 purpose = BREEDING、species = POULTRY、status = ACTIVE 的批次列表项，只取 B07 用到的字段）。
 */
public class FlockOptionVO {

    private long batchId;
    @Nullable
    private String batchNo;
    private long breedId;
    @Nullable
    private String breedName;
    private int currentCount;
    @Nullable
    private List<PenStock> pens;

    public long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    public long getBreedId() {
        return breedId;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    @NonNull
    public List<PenStock> getPens() {
        return pens == null ? Collections.emptyList() : pens;
    }

    /** 在栏栏舍 */
    public static class PenStock {

        private long penId;
        @Nullable
        private String penName;
        private int stockCount;

        public long getPenId() {
            return penId;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        public int getStockCount() {
            return stockCount;
        }
    }

    /** “栏舍名 · 批次号” */
    @androidx.annotation.NonNull
    public String flockText() {
        String pen = pens == null || pens.isEmpty() ? null : pens.get(0).getPenName();
        return pen == null ? String.valueOf(batchNo) : pen + " · " + batchNo;
    }
}
