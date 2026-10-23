package com.zhimu.slmp.ai.model;

import androidx.annotation.NonNull;

/**
 * 打开 K05 的参数：课程 ID 与入口（PlayEntry：COURSE、TOPIC、CONTINUE、HISTORY、KNOWLEDGE，START 上报时带上）。
 */
public final class PlayArgs {

    private final long courseId;
    @NonNull
    private final String entryType;

    public PlayArgs(long courseId, @NonNull String entryType) {
        this.courseId = courseId;
        this.entryType = entryType;
    }

    public long getCourseId() {
        return courseId;
    }

    @NonNull
    public String getEntryType() {
        return entryType;
    }
}
