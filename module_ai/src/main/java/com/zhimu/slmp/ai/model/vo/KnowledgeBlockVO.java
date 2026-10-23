package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 知识正文段落。para_type：TEXT / HEADING / QUOTE / LIST / IMAGE
 */
public class KnowledgeBlockVO {

    private String paraKey;
    private String paraType;
    private String text;
    private String contentHtml;
    private String imageUrl;

    @Nullable
    public String getParaKey() {
        return paraKey;
    }

    @Nullable
    public String getParaType() {
        return paraType;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getContentHtml() {
        return contentHtml;
    }

    @Nullable
    public String getImageUrl() {
        return imageUrl;
    }

    public void setParaKey(@Nullable String paraKey) {
        this.paraKey = paraKey;
    }

    public void setParaType(@Nullable String paraType) {
        this.paraType = paraType;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public void setContentHtml(@Nullable String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public void setImageUrl(@Nullable String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
