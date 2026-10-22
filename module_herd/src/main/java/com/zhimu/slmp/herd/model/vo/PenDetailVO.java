package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 栏舍详情（API-L08，L11、L12、L13、L27 也返回本结构）。
 */
public class PenDetailVO {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private long penId;
    private long categoryId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private String categoryName;
    private int capacity;
    private int stockCount;
    private int remainingCapacity;
    private int overCapacityCount;
    private int version;
    private BigDecimal occupancyRate;
    private String status;
    private String displayStatus;
    private String displayStatusText;
    private boolean isBreedingPen;
    private boolean canDisable;
    private boolean canEnable;
    private String disableBlockReason;
    private String disabledAt;
    private List<UserBriefVO> staff;
    private List<PenBatchItem> batches;
    private String unitText;

    public long getPenId() { return penId; }

    public long getCategoryId() { return categoryId; }

    public String getPenCode() { return penCode; }

    public String getPenName() { return penName; }

    public String getPenType() { return penType; }

    @Nullable
    public String getPenTypeText() { return penTypeText; }

    public String getCategoryName() { return categoryName; }

    public int getCapacity() { return capacity; }

    public int getStockCount() { return stockCount; }

    /** 还可转入（不小于 0） */
    public int getRemainingCapacity() { return remainingCapacity; }

    /** 已超出容量的数量 */
    public int getOverCapacityCount() { return overCapacityCount; }

    public int getVersion() { return version; }

    @Nullable
    public BigDecimal getOccupancyRate() { return occupancyRate; }

    /** ENABLED / DISABLED */
    public String getStatus() { return status; }

    public String getDisplayStatus() { return displayStatus; }

    @Nullable
    public String getDisplayStatusText() { return displayStatusText; }

    public boolean isBreedingPen() { return isBreedingPen; }

    public boolean isCanDisable() { return canDisable; }

    public boolean isCanEnable() { return canEnable; }

    @Nullable
    public String getDisableBlockReason() { return disableBlockReason; }

    @Nullable
    public String getDisabledAt() { return disabledAt; }

    public List<UserBriefVO> getStaff() { return staff == null ? Collections.emptyList() : staff; }

    public List<PenBatchItem> getBatches() { return batches == null ? Collections.emptyList() : batches; }

    @Nullable
    public String getUnitText() { return unitText; }

    public boolean isDisabled() { return STATUS_DISABLED.equals(status); }

    /** 栏舍内的在栏批次 */
    public static class PenBatchItem {
        private long batchId;
        private String batchNo;
        private String breedTitle;
        private int dayAge;
        private int qtyInPen;
        private boolean inWithdrawal;

        public long getBatchId() { return batchId; }

        public String getBatchNo() { return batchNo; }

        public String getBreedTitle() { return breedTitle; }

        public int getDayAge() { return dayAge; }

        public int getQtyInPen() { return qtyInPen; }

        public boolean isInWithdrawal() { return inWithdrawal; }
    }
}
