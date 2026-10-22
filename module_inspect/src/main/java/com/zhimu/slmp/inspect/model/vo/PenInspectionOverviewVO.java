package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-I12 栏舍近 7 天概况（I04 标题区、规则横幅、7 天格）：day_tag 为 ABNORMAL、BACKFILL、OVERDUE、DONE、MISSED、PENDING、NONE。
 */
public class PenInspectionOverviewVO {

    private long penId;
    private String penName;
    private List<String> batchNos;
    private List<String> responsibleNames;
    private int recent7DoneDays;
    private int recent7ShouldDays;
    private String today;
    private String backfillFrom;
    private String backfillTo;
    private List<DayCell> days;

    public long getPenId() { return penId; }

    public String getPenName() { return penName; }

    @NonNull
    public List<String> getBatchNos() { return batchNos == null ? Collections.emptyList() : batchNos; }

    @NonNull
    public List<String> getResponsibleNames() {
        return responsibleNames == null ? Collections.emptyList() : responsibleNames;
    }

    public int getRecent7DoneDays() { return recent7DoneDays; }

    public int getRecent7ShouldDays() { return recent7ShouldDays; }

    public String getToday() { return today; }

    public String getBackfillFrom() { return backfillFrom; }

    public String getBackfillTo() { return backfillTo; }

    @NonNull
    public List<DayCell> getDays() { return days == null ? Collections.emptyList() : days; }

    public static class DayCell {
        private String date;
        private String dayTag;
        private String abnormalSource;
        private Long inspectionId;

        public String getDate() { return date; }

        public String getDayTag() { return dayTag; }

        @Nullable
        public String getAbnormalSource() { return abnormalSource; }

        @Nullable
        public Long getInspectionId() { return inspectionId; }
    }
}
