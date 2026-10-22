package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 记录中的栏舍。
 */
public class PenBriefVO {

    private long penId;
    @Nullable
    private String name;
    @Nullable
    private String penTypeText;

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getPenTypeText() {
        return penTypeText;
    }
}
