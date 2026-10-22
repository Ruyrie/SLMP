package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-D30 任务数与提醒预估（纯计算，不全时 ready = false）。
 */
public class DisinfectPlanPreviewVO {

    private boolean ready;
    @Nullable
    private Integer times;
    @Nullable
    private Integer taskCount;
    @Nullable
    private Integer generateNowCount;
    @Nullable
    private String readyText;
    @Nullable
    private String penText;
    @Nullable
    private String remindText;
    @Nullable
    private String regenerateFrom;

    public boolean isReady() {
        return ready;
    }

    @Nullable
    public Integer getTimes() {
        return times;
    }

    @Nullable
    public Integer getTaskCount() {
        return taskCount;
    }

    @Nullable
    public Integer getGenerateNowCount() {
        return generateNowCount;
    }

    @Nullable
    public String getReadyText() {
        return readyText;
    }

    @Nullable
    public String getPenText() {
        return penText;
    }

    @Nullable
    public String getRemindText() {
        return remindText;
    }

    @Nullable
    public String getRegenerateFrom() {
        return regenerateFrom;
    }
}
