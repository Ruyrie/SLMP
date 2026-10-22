package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 消息中心列表项；route 为客户端要打开的页面编号（RouterTable）。
 */
public class MessageBriefVO {

    private long messageId;
    /** MSG_CATEGORY */
    @Nullable
    private String category;
    @Nullable
    private String msgType;
    /** NORMAL / IMPORTANT / URGENT */
    @Nullable
    private String level;
    @Nullable
    private String title;
    @Nullable
    private String summary;
    @Nullable
    private String tagText;
    @Nullable
    private FarmBriefVO farm;
    @Nullable
    private String bizType;
    @Nullable
    private Long bizId;
    @Nullable
    private String route;
    private boolean read;
    @Nullable
    private String createdAt;

    public long getMessageId() {
        return messageId;
    }

    @Nullable
    public String getCategory() {
        return category;
    }

    @Nullable
    public String getMsgType() {
        return msgType;
    }

    @Nullable
    public String getLevel() {
        return level;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSummary() {
        return summary;
    }

    @Nullable
    public String getTagText() {
        return tagText;
    }

    @Nullable
    public FarmBriefVO getFarm() {
        return farm;
    }

    @Nullable
    public String getBizType() {
        return bizType;
    }

    @Nullable
    public Long getBizId() {
        return bizId;
    }

    @Nullable
    public String getRoute() {
        return route;
    }

    public boolean isRead() {
        return read;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }
}
