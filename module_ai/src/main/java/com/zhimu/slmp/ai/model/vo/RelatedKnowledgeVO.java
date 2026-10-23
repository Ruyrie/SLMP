package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 相关条目
 */
public class RelatedKnowledgeVO {

    private long id;
    private String title;
    private String categoryName;
    private String sourceType;
    private String sourceTypeText;
    private int versionNo;
    private String iconType;

    public long getId() {
        return id;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getSourceType() {
        return sourceType;
    }

    @Nullable
    public String getSourceTypeText() {
        return sourceTypeText;
    }

    public int getVersionNo() {
        return versionNo;
    }

    @Nullable
    public String getIconType() {
        return iconType;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setCategoryName(@Nullable String categoryName) {
        this.categoryName = categoryName;
    }

    public void setSourceType(@Nullable String sourceType) {
        this.sourceType = sourceType;
    }

    public void setSourceTypeText(@Nullable String sourceTypeText) {
        this.sourceTypeText = sourceTypeText;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public void setIconType(@Nullable String iconType) {
        this.iconType = iconType;
    }
}
