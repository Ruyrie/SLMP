package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-K12 知识原文（K03）
 */
public class KnowledgeDetailVO {

    private long id;
    private String title;
    private String categoryName;
    private String sourceType;
    private String sourceTypeText;
    private int versionNo;
    private String updatedAt;
    private String shareUrl;
    private EmbeddedVideoVO embeddedVideo;
    private List<KnowledgeBlockVO> blocks;
    private List<RelatedKnowledgeVO> relatedItems;
    private String highlightParaKey;
    private Integer citedVersionNo;
    private boolean updatedAfterAnswer;

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
    public String getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public String getShareUrl() {
        return shareUrl;
    }

    @Nullable
    public EmbeddedVideoVO getEmbeddedVideo() {
        return embeddedVideo;
    }

    @NonNull
    public List<KnowledgeBlockVO> getBlocks() {
        return blocks == null ? Collections.emptyList() : blocks;
    }

    @NonNull
    public List<RelatedKnowledgeVO> getRelatedItems() {
        return relatedItems == null ? Collections.emptyList() : relatedItems;
    }

    @Nullable
    public String getHighlightParaKey() {
        return highlightParaKey;
    }

    @Nullable
    public Integer getCitedVersionNo() {
        return citedVersionNo;
    }

    public boolean isUpdatedAfterAnswer() {
        return updatedAfterAnswer;
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

    public void setUpdatedAt(@Nullable String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setShareUrl(@Nullable String shareUrl) {
        this.shareUrl = shareUrl;
    }

    public void setEmbeddedVideo(@Nullable EmbeddedVideoVO embeddedVideo) {
        this.embeddedVideo = embeddedVideo;
    }

    public void setBlocks(@Nullable List<KnowledgeBlockVO> blocks) {
        this.blocks = blocks;
    }

    public void setRelatedItems(@Nullable List<RelatedKnowledgeVO> relatedItems) {
        this.relatedItems = relatedItems;
    }

    public void setHighlightParaKey(@Nullable String highlightParaKey) {
        this.highlightParaKey = highlightParaKey;
    }

    public void setCitedVersionNo(@Nullable Integer citedVersionNo) {
        this.citedVersionNo = citedVersionNo;
    }

    public void setUpdatedAfterAnswer(boolean updatedAfterAnswer) {
        this.updatedAfterAnswer = updatedAfterAnswer;
    }
}
