package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 打开消息的结果（API-T15）：去向、失效与无权说明、最新未读数。
 */
public class OpenResultVO {

    /** OK / DELETED / NO_PERMISSION / NOT_MEMBER / FARM_FROZEN / NO_JUMP */
    @Nullable
    private String targetStatus;
    @Nullable
    private String targetStatusText;
    @Nullable
    private LinkVO link;
    /** BATCH / DIAGNOSIS / SALE_ORDER / LOT / TODO / CHANGE_REQUEST */
    @Nullable
    private String deletedRecordType;
    /** T08 顶部栏标题，例如“批次档案” */
    @Nullable
    private String pageTitle;
    @Nullable
    private String deletedTitle;
    @Nullable
    private String deletedDesc;
    @Nullable
    private String denyTitle;
    @Nullable
    private String denyDesc;
    private int totalUnread;

    @Nullable
    public String getTargetStatus() {
        return targetStatus;
    }

    @Nullable
    public String getTargetStatusText() {
        return targetStatusText;
    }

    @Nullable
    public LinkVO getLink() {
        return link;
    }

    @Nullable
    public String getDeletedRecordType() {
        return deletedRecordType;
    }

    @Nullable
    public String getPageTitle() {
        return pageTitle;
    }

    @Nullable
    public String getDeletedTitle() {
        return deletedTitle;
    }

    @Nullable
    public String getDeletedDesc() {
        return deletedDesc;
    }

    @Nullable
    public String getDenyTitle() {
        return denyTitle;
    }

    @Nullable
    public String getDenyDesc() {
        return denyDesc;
    }

    public int getTotalUnread() {
        return totalUnread;
    }
}
