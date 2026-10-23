package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 平台更正说明
 */
public class CorrectionVO {

    private String content;
    private String displayTime;

    @Nullable
    public String getContent() {
        return content;
    }

    @Nullable
    public String getDisplayTime() {
        return displayTime;
    }

    public void setContent(@Nullable String content) {
        this.content = content;
    }

    public void setDisplayTime(@Nullable String displayTime) {
        this.displayTime = displayTime;
    }
}
