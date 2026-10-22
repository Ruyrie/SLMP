package com.zhimu.slmp.feed.ui.weigh;

import androidx.annotation.Nullable;

/**
 * G05 结果卡的生长预判：status 为 SLOW / FAST / NORMAL / NO_STD；tagText 只在偏慢、偏快时有值
 * （trending_down / trending_up）；note 为卡片底部说明。
 */
public final class GrowthPreview {

    @Nullable
    public final String status;
    @Nullable
    public final String tagText;
    @Nullable
    public final String note;

    public GrowthPreview(@Nullable String status, @Nullable String tagText, @Nullable String note) {
        this.status = status;
        this.tagText = tagText;
        this.note = note;
    }

    public boolean isFast() {
        return "FAST".equals(status);
    }
}
