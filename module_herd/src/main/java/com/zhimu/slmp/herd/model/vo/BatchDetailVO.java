package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 批次档案（API-L15；API-L20 修改入栏也返回本结构）。intro_unit_price、pending_change_request_count 为管理视图，
 * 员工请求时不返回（为 null，前端不渲染）。
 */
public class BatchDetailVO {

    public static final String SOURCE_PURCHASED = "PURCHASED";
    public static final String SOURCE_SELF_BRED = "SELF_BRED";

    private long batchId;
    private long farmId;
    private long categoryId;
    private long breedId;
    private long entryEventId;
    private String batchNo;
    private String farmName;
    private String categoryName;
    private String breedName;
    private String breedTitle;
    private String purpose;
    private String source;
    private String status;
    private String entryDate;
    private String qrContent;
    private String labelText;
    private String sourceRecordType;
    private String endType;
    private String endDate;
    private String stageName;
    private List<BatchPenItem> pens;
    private PenBriefVO entryPen;
    private int dayAge;
    private int daysRaised;
    private int currentCount;
    private int entryCount;
    private int deathCount;
    private int soldCount;
    private int entryAgeDays;
    private int version;
    private BigDecimal mortalityRate;
    private BigDecimal entryAvgWeight;
    private BigDecimal introUnitPrice;
    private Integer pendingChangeRequestCount;
    private WithdrawalInfo withdrawal;
    private BatchActions actions;
    private String unit;
    private String unitText;

    public long getBatchId() { return batchId; }

    public long getFarmId() { return farmId; }

    public long getCategoryId() { return categoryId; }

    public long getBreedId() { return breedId; }

    public long getEntryEventId() { return entryEventId; }

    public String getBatchNo() { return batchNo; }

    public String getFarmName() { return farmName; }

    public String getCategoryName() { return categoryName; }

    public String getBreedName() { return breedName; }

    public String getBreedTitle() { return breedTitle; }

    /** COMMERCIAL / BREEDING */
    public String getPurpose() { return purpose; }

    /** PURCHASED / SELF_BRED */
    public String getSource() { return source; }

    /** ACTIVE / ENDED */
    public String getStatus() { return status; }

    public String getEntryDate() { return entryDate; }

    public String getQrContent() { return qrContent; }

    public String getLabelText() { return labelText; }

    @Nullable
    public String getSourceRecordType() { return sourceRecordType; }

    @Nullable
    public String getEndType() { return endType; }

    @Nullable
    public String getEndDate() { return endDate; }

    @Nullable
    public String getStageName() { return stageName; }

    public List<BatchPenItem> getPens() { return pens == null ? Collections.emptyList() : pens; }

    @Nullable
    public PenBriefVO getEntryPen() { return entryPen; }

    public int getDayAge() { return dayAge; }

    public int getDaysRaised() { return daysRaised; }

    public int getCurrentCount() { return currentCount; }

    public int getEntryCount() { return entryCount; }

    public int getDeathCount() { return deathCount; }

    public int getSoldCount() { return soldCount; }

    public int getEntryAgeDays() { return entryAgeDays; }

    public int getVersion() { return version; }

    @Nullable
    public BigDecimal getMortalityRate() { return mortalityRate; }

    @Nullable
    public BigDecimal getEntryAvgWeight() { return entryAvgWeight; }

    /** 管理视图，员工为 null */
    @Nullable
    public BigDecimal getIntroUnitPrice() { return introUnitPrice; }

    /** 管理视图，员工为 null */
    @Nullable
    public Integer getPendingChangeRequestCount() { return pendingChangeRequestCount; }

    @Nullable
    public WithdrawalInfo getWithdrawal() { return withdrawal; }

    @Nullable
    public BatchActions getActions() { return actions; }

    public String getUnit() { return unit; }

    @Nullable
    public String getUnitText() { return unitText; }

    public boolean isBreeding() { return BatchListItemVO.PURPOSE_BREEDING.equals(purpose); }

    public boolean isActive() { return BatchListItemVO.STATUS_ACTIVE.equals(status); }

    public boolean isPurchased() { return SOURCE_PURCHASED.equals(source); }

    /** 休药信息（模块08 计算） */
    public static class WithdrawalInfo {
        private boolean inWithdrawal;
        private Integer remainingDays;
        private Integer withdrawalDays;
        private String sellableDate;
        private String lastDrugName;
        private String lastDrugDate;

        public boolean isInWithdrawal() { return inWithdrawal; }

        @Nullable
        public Integer getRemainingDays() { return remainingDays; }

        @Nullable
        public Integer getWithdrawalDays() { return withdrawalDays; }

        @Nullable
        public String getSellableDate() { return sellableDate; }

        @Nullable
        public String getLastDrugName() { return lastDrugName; }

        @Nullable
        public String getLastDrugDate() { return lastDrugDate; }
    }

    /** 当前用户可执行的批次操作（员工为 false 的项不渲染） */
    public static class BatchActions {
        private boolean canTransfer;
        private boolean canRegisterDeath;
        private boolean canSubmitChangeRequest;
        private boolean canEditEntry;
        private boolean canDelete;

        public boolean isCanTransfer() { return canTransfer; }

        public boolean isCanRegisterDeath() { return canRegisterDeath; }

        public boolean isCanSubmitChangeRequest() { return canSubmitChangeRequest; }

        public boolean isCanEditEntry() { return canEditEntry; }

        public boolean isCanDelete() { return canDelete; }

        public boolean any() {
            return canTransfer || canRegisterDeath || canSubmitChangeRequest || canEditEntry || canDelete;
        }
    }
}
