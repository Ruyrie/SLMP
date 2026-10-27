package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** Public traceability allowlist, deliberately separate from the management archive model. */
public final class BatchTraceVO {
    private String batchNo;
    private String farmName;
    private String categoryName;
    private String breedName;
    private String source;
    private String status;
    private String entryDate;
    private String endDate;
    private Integer entryAgeDays;
    private BigDecimal entryAvgWeight;
    private String updatedAt;
    private List<Event> events;

    @Nullable public String getBatchNo() { return batchNo; }
    @Nullable public String getFarmName() { return farmName; }
    @Nullable public String getCategoryName() { return categoryName; }
    @Nullable public String getBreedName() { return breedName; }
    @Nullable public String getSource() { return source; }
    @Nullable public String getStatus() { return status; }
    @Nullable public String getEntryDate() { return entryDate; }
    @Nullable public String getEndDate() { return endDate; }
    @Nullable public Integer getEntryAgeDays() { return entryAgeDays; }
    @Nullable public BigDecimal getEntryAvgWeight() { return entryAvgWeight; }
    @Nullable public String getUpdatedAt() { return updatedAt; }
    @NonNull public List<Event> getEvents() { return events == null ? Collections.emptyList() : events; }

    public static final class Event {
        private String type;
        private String date;
        private String name;
        private Integer quantity;
        private BigDecimal avgWeight;
        private String withdrawalEndDate;
        private String updatedAt;

        @Nullable public String getType() { return type; }
        @Nullable public String getDate() { return date; }
        @Nullable public String getName() { return name; }
        @Nullable public Integer getQuantity() { return quantity; }
        @Nullable public BigDecimal getAvgWeight() { return avgWeight; }
        @Nullable public String getWithdrawalEndDate() { return withdrawalEndDate; }
        @Nullable public String getUpdatedAt() { return updatedAt; }
    }
}
