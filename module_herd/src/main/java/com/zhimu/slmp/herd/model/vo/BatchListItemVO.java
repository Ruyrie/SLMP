package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-L06 批次列表 / 下拉（L03 批次视图、L10、L11 批次下拉）。
 */
public class BatchListItemVO {

    public static final String PURPOSE_COMMERCIAL = "COMMERCIAL";
    public static final String PURPOSE_BREEDING = "BREEDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    private long batchId;
    private long breedId;
    private String batchNo;
    private String breedName;
    private String categoryName;
    private String breedTitle;
    private String entryDate;
    private String purpose;
    private String source;
    private String status;
    private List<BatchPenItem> pens;
    private int entryAgeDays;
    private int dayAge;
    private int currentCount;
    private BigDecimal mortalityRate;
    private boolean inWithdrawal;
    private String stageName;
    private String endDate;
    private String unitText;

    public long getBatchId() { return batchId; }

    public long getBreedId() { return breedId; }

    public String getBatchNo() { return batchNo; }

    public String getBreedName() { return breedName; }

    public String getCategoryName() { return categoryName; }

    public String getBreedTitle() { return breedTitle; }

    public String getEntryDate() { return entryDate; }

    public String getPurpose() { return purpose; }

    public String getSource() { return source; }

    public String getStatus() { return status; }

    public List<BatchPenItem> getPens() { return pens == null ? Collections.emptyList() : pens; }

    public int getEntryAgeDays() { return entryAgeDays; }

    public int getDayAge() { return dayAge; }

    public int getCurrentCount() { return currentCount; }

    @Nullable
    public BigDecimal getMortalityRate() { return mortalityRate; }

    public boolean isInWithdrawal() { return inWithdrawal; }

    @Nullable
    public String getStageName() { return stageName; }

    @Nullable
    public String getEndDate() { return endDate; }

    @Nullable
    public String getUnitText() { return unitText; }

    public boolean isBreeding() { return PURPOSE_BREEDING.equals(purpose); }
}
