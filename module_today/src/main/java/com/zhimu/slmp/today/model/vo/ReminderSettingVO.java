package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 提醒设置（API-T10、T11）。
 */
public class ReminderSettingVO {

    private boolean todoRemindEnabled;
    private boolean digestEnabled;
    @Nullable
    private String digestTime;
    private boolean quietEnabled;
    @Nullable
    private String quietStart;
    @Nullable
    private String quietEnd;
    private boolean quietEndNextDay;
    @Nullable
    private String summaryText;

    public boolean isTodoRemindEnabled() {
        return todoRemindEnabled;
    }

    public boolean isDigestEnabled() {
        return digestEnabled;
    }

    @Nullable
    public String getDigestTime() {
        return digestTime;
    }

    public boolean isQuietEnabled() {
        return quietEnabled;
    }

    @Nullable
    public String getQuietStart() {
        return quietStart;
    }

    @Nullable
    public String getQuietEnd() {
        return quietEnd;
    }

    public boolean isQuietEndNextDay() {
        return quietEndNextDay;
    }

    @Nullable
    public String getSummaryText() {
        return summaryText;
    }
}
