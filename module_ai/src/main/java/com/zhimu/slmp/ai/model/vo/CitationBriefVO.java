package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 回答引用的知识条目快照（seq_no 与正文 [^n] 对应）。source_type：PLATFORM / DISEASE / DRUG_CATALOG；icon_type：STANDARD / ARTICLE
 */
public class CitationBriefVO {

    private long id;
    private int seqNo;
    private long knowledgeId;
    private String title;
    private String categoryName;
    private String sourceType;
    private String iconType;

    public long getId() {
        return id;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public long getKnowledgeId() {
        return knowledgeId;
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
    public String getIconType() {
        return iconType;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
    }

    public void setKnowledgeId(long knowledgeId) {
        this.knowledgeId = knowledgeId;
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

    public void setIconType(@Nullable String iconType) {
        this.iconType = iconType;
    }
}
