package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * L01 生产提醒项（休药中、未处理异常上报、库存不足等）。
 */
public class ProductionReminderItemVO {

    /** 未处理异常上报：route 为 I06，带 report_id */
    public static final String TYPE_ABNORMAL_REPORT = "ABNORMAL_REPORT";

    /** ReminderType：WITHDRAWAL、ABNORMAL_REPORT、FEED_LOW_STOCK、DRUG_LOW_STOCK、URGENT_DIAGNOSIS 等 */
    private String reminderType;
    private boolean isWarning;
    private String title;
    private String subtitle;
    private String tagText;
    private Long batchId;
    private Long todoId;
    /** 异常上报编号，仅 ABNORMAL_REPORT 有值 */
    private Long reportId;
    /** 点击进入的页面编号，例如 D05、M01、I06 */
    private String route;

    public String getReminderType() {
        return reminderType;
    }

    public boolean isWarning() {
        return isWarning;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    @Nullable
    public String getTagText() {
        return tagText;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public Long getTodoId() {
        return todoId;
    }

    @Nullable
    public Long getReportId() {
        return reportId;
    }

    @Nullable
    public String getRoute() {
        return route;
    }
}
