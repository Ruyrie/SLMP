package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * API-I01 巡检统计与截止时间（I01 统计卡）。scope：ALL_PENS 有在栏批次的栏舍 / MY_PENS 负责栏舍。
 */
public class InspectionSummaryVO {

    public static final String SCOPE_ALL = "ALL_PENS";
    public static final String SCOPE_MY = "MY_PENS";

    private String farmName;
    private String date;
    private String deadlineTime;
    private String remindTime;
    private String scope;
    private int shouldCount;
    private int doneCount;
    private int pendingCount;
    private int abnormalCount;
    private int missedCount;
    private Integer responsiblePenCount;
    private Integer noOwnerPendingCount;
    private BigDecimal completionRate;
    private boolean deadlinePassed;
    private boolean allDone;
    private boolean canEditDeadline;
    private String firstAbnormalPenName;
    private Boolean firstAbnormalReported;

    public String getFarmName() { return farmName; }

    public String getDate() { return date; }

    public String getDeadlineTime() { return deadlineTime; }

    public String getRemindTime() { return remindTime; }

    public String getScope() { return scope; }

    public int getShouldCount() { return shouldCount; }

    public int getDoneCount() { return doneCount; }

    public int getPendingCount() { return pendingCount; }

    public int getAbnormalCount() { return abnormalCount; }

    public int getMissedCount() { return missedCount; }

    @Nullable
    public Integer getResponsiblePenCount() { return responsiblePenCount; }

    @Nullable
    public Integer getNoOwnerPendingCount() { return noOwnerPendingCount; }

    /** 已巡 / 应巡（四位小数）；应巡为 0 时为 null */
    @Nullable
    public BigDecimal getCompletionRate() { return completionRate; }

    public boolean isDeadlinePassed() { return deadlinePassed; }

    public boolean isAllDone() { return allDone; }

    public boolean isCanEditDeadline() { return canEditDeadline; }

    @Nullable
    public String getFirstAbnormalPenName() { return firstAbnormalPenName; }

    @Nullable
    public Boolean getFirstAbnormalReported() { return firstAbnormalReported; }

    public boolean isMyPens() { return SCOPE_MY.equals(scope); }
}
