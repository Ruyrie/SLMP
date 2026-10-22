package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** G04 需要关注（API-G15）：ABNORMAL 投喂异常 / OVERDUE 逾期未登记，按日期、餐次倒序。 */
public class FeedAttentionItemVO {

    public static final String TYPE_ABNORMAL = "ABNORMAL";
    public static final String TYPE_OVERDUE = "OVERDUE";

    private String type;
    private String date;
    private String mealName;
    private Long batchId;
    private String batchNo;
    private String penName;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private String executorName;
    private String abnormalType;
    private BigDecimal deviationRatio;
    private String taskStatus;
    private String taskStatusText;
    private Long taskId;
    private Long recordId;
    private Boolean canBackfill;
    private Boolean isToday;

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getDate() {
        return date;
    }

    @Nullable
    public String getMealName() {
        return mealName;
    }

    public long getBatchId() {
        return batchId == null ? 0L : batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenName() {
        return penName;
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
    public String getExecutorName() {
        return executorName;
    }

    @Nullable
    public String getAbnormalType() {
        return abnormalType;
    }

    @Nullable
    public BigDecimal getDeviationRatio() {
        return deviationRatio;
    }

    @Nullable
    public String getTaskStatus() {
        return taskStatus;
    }

    @Nullable
    public String getTaskStatusText() {
        return taskStatusText;
    }

    public long getTaskId() {
        return taskId == null ? 0L : taskId;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    public boolean isCanBackfill() {
        return Boolean.TRUE.equals(canBackfill);
    }

    public boolean isToday() {
        return Boolean.TRUE.equals(isToday);
    }

    public boolean isOverdueType() {
        return TYPE_OVERDUE.equals(type);
    }
}
