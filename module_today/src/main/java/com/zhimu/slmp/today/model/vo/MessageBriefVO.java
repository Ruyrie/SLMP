package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 消息中心列表项（API-T12 列表项、API-T18 data）：在 lib_common 的公共 MessageBriefVO 基础上补充本模块字段。
 * 显示与分组取 sort_time；date_group 为 TODAY / YESTERDAY / EARLIER。
 */
public class MessageBriefVO extends com.zhimu.slmp.common.model.vo.MessageBriefVO {

    public static final String CATEGORY_TODO = "TODO";
    public static final String CATEGORY_AI = "AI";
    public static final String CATEGORY_AUDIT = "AUDIT";
    public static final String CATEGORY_MEMBER = "MEMBER";
    public static final String CATEGORY_LOT = "LOT";
    public static final String CATEGORY_NOTICE = "NOTICE";
    public static final String CATEGORY_OTHER = "OTHER";

    public static final String TYPE_FARM_INVITE = "FARM_INVITE";
    public static final String TYPE_NOTICE = "NOTICE";
    public static final String TYPE_OWNER_TRANSFER = "OWNER_TRANSFER";
    public static final String TYPE_FARM_FROZEN = "FARM_FROZEN";

    @Nullable
    private String categoryText;
    @Nullable
    private String levelText;
    /** 标签语义：GENERAL / ATTENTION / URGENT / PASSED / REJECTED / RECALLED / IMPORTANT / DONE / IGNORED */
    @Nullable
    private String tagCode;
    @Nullable
    private String webRoute;
    @Nullable
    private Long todoId;
    private boolean revoked;
    @Nullable
    private String sortTime;
    @Nullable
    private String dateGroup;
    /** PENDING / OVERDUE / DONE / IGNORED / HANDLED / DEFAULT */
    @Nullable
    private String iconState;
    private int mergeCount;
    @Nullable
    private MessageActionVO action;

    @Nullable
    public String getCategoryText() {
        return categoryText;
    }

    @Nullable
    public String getLevelText() {
        return levelText;
    }

    @Nullable
    public String getTagCode() {
        return tagCode;
    }

    @Nullable
    public String getWebRoute() {
        return webRoute;
    }

    @Nullable
    public Long getTodoId() {
        return todoId;
    }

    public boolean isRevoked() {
        return revoked;
    }

    @Nullable
    public String getSortTime() {
        return sortTime;
    }

    @Nullable
    public String getDateGroup() {
        return dateGroup;
    }

    @Nullable
    public String getIconState() {
        return iconState;
    }

    public int getMergeCount() {
        return mergeCount;
    }

    @Nullable
    public MessageActionVO getAction() {
        return action;
    }

    public boolean isInvite() {
        return TYPE_FARM_INVITE.equals(getMsgType());
    }
}
