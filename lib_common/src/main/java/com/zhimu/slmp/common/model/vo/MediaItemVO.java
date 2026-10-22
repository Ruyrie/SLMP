package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 记录中的照片和视频（FileVO 全部字段 + 排序与来源说明），用于媒体列表与全屏预览 I09。
 */
public class MediaItemVO extends FileVO {

    private int sortNo;
    /** 例如“巡检” */
    @Nullable
    private String sourceText;
    @Nullable
    private String shotBy;
    @Nullable
    private String shotAt;

    public int getSortNo() {
        return sortNo;
    }

    @Nullable
    public String getSourceText() {
        return sourceText;
    }

    @Nullable
    public String getShotBy() {
        return shotBy;
    }

    @Nullable
    public String getShotAt() {
        return shotAt;
    }
}
