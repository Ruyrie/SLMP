package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * L01 生产提醒项（休药中、库存不足等）。
 */
public class ProductionReminderItemVO {

    /** ReminderType：WITHDRAWAL、FEED_LOW_STOCK、DRUG_LOW_STOCK、URGENT_DIAGNOSIS 等 */
    private String reminderType;
    private boolean isWarning;
    private String title;
    private String subtitle;
    private String tagText;
    private Long batchId;
    private Long todoId;
    /** 点击进入的页面编号，例如 D05、M01 */
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
    public String getRoute() {
        return route;
    }
}
