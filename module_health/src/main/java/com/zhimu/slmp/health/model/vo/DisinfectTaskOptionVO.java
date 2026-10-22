package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-D37 登记消毒的任务下拉：养殖户为今天待执行与全部逾期，员工为负责栏舍今天待执行。
 */
public class DisinfectTaskOptionVO {

    private long taskId;
    private long penId;
    private boolean overdue;
    @Nullable
    private String penName;
    @Nullable
    private String taskDate;
    @Nullable
    private String dateText;
    @Nullable
    private String planText;
    @Nullable
    private String planConcentration;
    @Nullable
    private String methodCode;
    @Nullable
    private Long drugId;
    @Nullable
    private Long defaultLotId;
    /** 员工不输出 */
    @Nullable
    private Long defaultExecutorId;

    public long getTaskId() {
        return taskId;
    }

    public long getPenId() {
        return penId;
    }

    public boolean isOverdue() {
        return overdue;
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
    public String getPlanText() {
        return planText;
    }

    @Nullable
    public String getPlanConcentration() {
        return planConcentration;
    }

    @Nullable
    public String getMethodCode() {
        return methodCode;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public Long getDefaultLotId() {
        return defaultLotId;
    }

    @Nullable
    public Long getDefaultExecutorId() {
        return defaultExecutorId;
    }
}
