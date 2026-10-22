package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * A07“提醒设置”摘要（模块03 本人提醒设置）。
 * 技术准备文档写在 common.model.vo，框架中尚未提供，暂放本组件。
 */
public class ReminderSummaryVO {

    private boolean digestEnabled;
    /** 07:00 */
    @Nullable
    private String digestTime;
    private boolean quietEnabled;
    @Nullable
    private String quietStart;
    @Nullable
    private String quietEnd;

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
}
