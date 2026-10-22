package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 待办列表项（API-T02、T04、T06 列表项，API-T05 items）。显示文字均由后端生成。
 */
public class TodoItemVO {

    private long todoId;
    private long farmId;
    @Nullable
    private String farmName;
    /** TODO_TYPE，例如 FEEDING */
    @Nullable
    private String todoType;
    @Nullable
    private String todoTypeText;
    /** WORK / WARNING / BUSINESS */
    @Nullable
    private String category;
    @Nullable
    private String categoryText;
    /** PENDING / OVERDUE / DONE / IGNORED */
    @Nullable
    private String displayStatus;
    @Nullable
    private String displayStatusText;
    @Nullable
    private String title;
    @Nullable
    private String subText;
    /** 尾部标签文字：已逾期、预警、今天、11:30、17:00 前、待处理、已完成、已忽略 */
    @Nullable
    private String tailText;
    @Nullable
    private String dueDate;
    @Nullable
    private String dueTime;
    /** AT_TIME / BEFORE_TIME / ALL_DAY */
    @Nullable
    private String timeType;
    /** 时间轴的时间列：全天、07:00 */
    @Nullable
    private String timeLabel;
    @Nullable
    private Integer overdueDays;
    @Nullable
    private Long penId;
    @Nullable
    private String penName;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    /** 员工不返回 */
    @Nullable
    private String assigneeText;
    /** 只有待收款有值，员工不返回 */
    @Nullable
    private BigDecimal amount;
    @Nullable
    private String completedText;
    @Nullable
    private Boolean overdueDone;
    @Nullable
    private String ignoreReason;
    @Nullable
    private String ignoreText;
    @Nullable
    private String actionText;
    /** 未完成时点击的去向 */
    @Nullable
    private LinkVO listLink;
    /** “去登记”的去向 */
    @Nullable
    private LinkVO registerLink;
    /** 乐观锁 */
    private int version;

    public long getTodoId() {
        return todoId;
    }

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public String getTodoType() {
        return todoType;
    }

    @Nullable
    public String getTodoTypeText() {
        return todoTypeText;
    }

    @Nullable
    public String getCategory() {
        return category;
    }

    @Nullable
    public String getCategoryText() {
        return categoryText;
    }

    @Nullable
    public String getDisplayStatus() {
        return displayStatus;
    }

    @Nullable
    public String getDisplayStatusText() {
        return displayStatusText;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSubText() {
        return subText;
    }

    @Nullable
    public String getTailText() {
        return tailText;
    }

    @Nullable
    public String getDueDate() {
        return dueDate;
    }

    @Nullable
    public String getDueTime() {
        return dueTime;
    }

    @Nullable
    public String getTimeType() {
        return timeType;
    }

    @Nullable
    public String getTimeLabel() {
        return timeLabel;
    }

    @Nullable
    public Integer getOverdueDays() {
        return overdueDays;
    }

    @Nullable
    public Long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getAssigneeText() {
        return assigneeText;
    }

    @Nullable
    public BigDecimal getAmount() {
        return amount;
    }

    @Nullable
    public String getCompletedText() {
        return completedText;
    }

    @Nullable
    public Boolean getOverdueDone() {
        return overdueDone;
    }

    @Nullable
    public String getIgnoreReason() {
        return ignoreReason;
    }

    @Nullable
    public String getIgnoreText() {
        return ignoreText;
    }

    @Nullable
    public String getActionText() {
        return actionText;
    }

    @Nullable
    public LinkVO getListLink() {
        return listLink;
    }

    @Nullable
    public LinkVO getRegisterLink() {
        return registerLink;
    }

    public int getVersion() {
        return version;
    }
}
