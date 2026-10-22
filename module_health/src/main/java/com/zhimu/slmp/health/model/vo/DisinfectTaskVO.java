package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D36 消毒任务列表项：已过截止时刻的 PENDING 返回 OVERDUE；actions 为 REGISTER、BACKFILL、VIEW_LOG。
 */
public class DisinfectTaskVO {

    private long id;
    private long penId;
    private int cycleDays;
    private boolean noStaff;
    @Nullable
    private List<String> actions;
    @Nullable
    private String penName;
    @Nullable
    private String taskDate;
    @Nullable
    private String dateText;
    @Nullable
    private String dueTime;
    @Nullable
    private String disinfectantName;
    @Nullable
    private String concentration;
    @Nullable
    private String methodName;
    @Nullable
    private String executorText;
    /** OVERDUE / PENDING / DONE */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private Long planId;
    @Nullable
    private Long recordId;
    @Nullable
    private String actualTime;
    @Nullable
    private String actualExecutorName;
    @Nullable
    private Boolean manualDone;

    public long getId() {
        return id;
    }

    public long getPenId() {
        return penId;
    }

    public int getCycleDays() {
        return cycleDays;
    }

    public boolean isNoStaff() {
        return noStaff;
    }

    @NonNull
    public List<String> getActions() {
        return actions == null ? Collections.emptyList() : actions;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getTaskDate() {
        return taskDate;
    }

    @Nullable
    public String getDateText() {
        return dateText;
    }

    @Nullable
    public String getDueTime() {
        return dueTime;
    }

    @Nullable
    public String getDisinfectantName() {
        return disinfectantName;
    }

    @Nullable
    public String getConcentration() {
        return concentration;
    }

    @Nullable
    public String getMethodName() {
        return methodName;
    }

    @Nullable
    public String getExecutorText() {
        return executorText;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public Long getPlanId() {
        return planId;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    @Nullable
    public String getActualTime() {
        return actualTime;
    }

    @Nullable
    public String getActualExecutorName() {
        return actualExecutorName;
    }

    @Nullable
    public Boolean getManualDone() {
        return manualDone;
    }
}
