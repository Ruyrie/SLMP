package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D28 消毒计划列表项（API-D31–D34 返回）；toast、task_count 仅 API-D31、D34。
 */
public class DisinfectPlanVO {

    private long id;
    private int version;
    private int cycleDays;
    @Nullable
    private List<Long> penIds;
    /** PAUSE、RESUME、EDIT */
    @Nullable
    private List<String> actions;
    @Nullable
    private String penNamesText;
    @Nullable
    private String startDate;
    @Nullable
    private String endDate;
    @Nullable
    private String dueTime;
    @Nullable
    private String disinfectantName;
    @Nullable
    private String concentration;
    @Nullable
    private String methodCode;
    @Nullable
    private String methodName;
    /** ACTIVE / PAUSED / ENDED */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String nextTaskDate;
    @Nullable
    private String nextTaskText;
    @Nullable
    private String pauseText;
    @Nullable
    private String toast;
    @Nullable
    private Integer taskCount;

    public long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public int getCycleDays() {
        return cycleDays;
    }

    @NonNull
    public List<Long> getPenIds() {
        return penIds == null ? Collections.emptyList() : penIds;
    }

    @NonNull
    public List<String> getActions() {
        return actions == null ? Collections.emptyList() : actions;
    }

    @Nullable
    public String getPenNamesText() {
        return penNamesText;
    }

    @Nullable
    public String getStartDate() {
        return startDate;
    }

    @Nullable
    public String getEndDate() {
        return endDate;
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
    public String getMethodCode() {
        return methodCode;
    }

    @Nullable
    public String getMethodName() {
        return methodName;
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
    public String getNextTaskDate() {
        return nextTaskDate;
    }

    @Nullable
    public String getNextTaskText() {
        return nextTaskText;
    }

    @Nullable
    public String getPauseText() {
        return pauseText;
    }

    @Nullable
    public String getToast() {
        return toast;
    }

    @Nullable
    public Integer getTaskCount() {
        return taskCount;
    }
}
