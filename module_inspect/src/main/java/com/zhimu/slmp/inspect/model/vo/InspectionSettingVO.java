package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-I04 / I05 巡检截止时间与提醒规则（I01 截止时间面板）。
 */
public class InspectionSettingVO {

    private String farmName;
    private String deadlineTime;
    private String remindTime;
    private String defaultDeadline;
    private List<String> quickOptions;
    private String minTime;
    private String maxTime;
    private List<String> ownerManagerNames;
    private String updatedByName;
    private String updatedAt;

    public String getFarmName() { return farmName; }

    public String getDeadlineTime() { return deadlineTime; }

    public String getRemindTime() { return remindTime; }

    public String getDefaultDeadline() { return defaultDeadline; }

    @NonNull
    public List<String> getQuickOptions() { return quickOptions == null ? Collections.emptyList() : quickOptions; }

    public String getMinTime() { return minTime; }

    public String getMaxTime() { return maxTime; }

    @NonNull
    public List<String> getOwnerManagerNames() {
        return ownerManagerNames == null ? Collections.emptyList() : ownerManagerNames;
    }

    @Nullable
    public String getUpdatedByName() { return updatedByName; }

    @Nullable
    public String getUpdatedAt() { return updatedAt; }
}
