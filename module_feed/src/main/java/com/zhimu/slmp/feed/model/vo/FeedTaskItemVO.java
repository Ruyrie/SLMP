package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** G01 今日任务行（API-G02）：按 feed_time 升序，同一餐按栏舍编号排序。 */
public class FeedTaskItemVO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_MANUAL_DONE = "MANUAL_DONE";
    public static final String STATUS_MISSED = "MISSED";

    public static final String ACTION_REGISTER = "REGISTER";
    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_VIEW = "VIEW";

    private Long taskId;
    private String taskDate;
    private Integer mealNo;
    private String mealCode;
    private String mealName;
    private String feedTime;
    private Long penId;
    private String penName;
    private Long batchId;
    private String batchNo;
    private Long feedId;
    private String feedName;
    private Boolean lowStock;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private String assigneeName;
    private String executorName;
    private String registeredAt;
    private String status;
    private String statusText;
    private String abnormalType;
    private Boolean isBackfill;
    private Integer overdueMinutes;
    private Long recordId;
    private String action;

    public long getTaskId() {
        return taskId == null ? 0L : taskId;
    }

    @Nullable
    public String getTaskDate() {
        return taskDate;
    }

    public int getMealNo() {
        return mealNo == null ? 0 : mealNo;
    }

    @Nullable
    public String getMealCode() {
        return mealCode;
    }

    @Nullable
    public String getMealName() {
        return mealName;
    }

    @Nullable
    public String getFeedTime() {
        return feedTime;
    }

    public long getPenId() {
        return penId == null ? 0L : penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    public long getBatchId() {
        return batchId == null ? 0L : batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    public long getFeedId() {
        return feedId == null ? 0L : feedId;
    }

    @Nullable
    public String getFeedName() {
        return feedName;
    }

    public boolean isLowStock() {
        return Boolean.TRUE.equals(lowStock);
    }

    @Nullable
    public BigDecimal getPlanQty() {
        return planQty;
    }

    @Nullable
    public BigDecimal getActualQty() {
        return actualQty;
    }

    @Nullable
    public String getAssigneeName() {
        return assigneeName;
    }

    @Nullable
    public String getExecutorName() {
        return executorName;
    }

    @Nullable
    public String getRegisteredAt() {
        return registeredAt;
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
    public String getAbnormalType() {
        return abnormalType;
    }

    public boolean isBackfill() {
        return Boolean.TRUE.equals(isBackfill);
    }

    @Nullable
    public Integer getOverdueMinutes() {
        return overdueMinutes;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    @Nullable
    public String getAction() {
        return action;
    }

    /** 已投喂（含手动完成） */
    public boolean isDone() {
        return STATUS_DONE.equals(status) || STATUS_MANUAL_DONE.equals(status);
    }

    public boolean isOverdue() {
        return STATUS_OVERDUE.equals(status);
    }

    /** 已投喂且偏差超过阈值 */
    public boolean isAbnormal() {
        return isDone() && abnormalType != null && !"NONE".equals(abnormalType);
    }
}
