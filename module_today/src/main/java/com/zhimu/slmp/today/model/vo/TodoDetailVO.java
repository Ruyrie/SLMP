package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

/**
 * 待办详情（API-T07–T09 data）。
 */
public class TodoDetailVO extends TodoItemVO {

    @Nullable
    private String subtitle;
    @Nullable
    private String sourceModuleName;
    @Nullable
    private String dueText;
    @Nullable
    private String dueHint;
    @Nullable
    private String receiversText;
    @Nullable
    private String receiversHint;
    @Nullable
    private String statusNote;
    /** 类型专属信息行标签，例如“消毒要求” */
    @Nullable
    private String detailLabel;
    @Nullable
    private String detailValue;
    private int remindCount;
    @Nullable
    private String remindText;
    @Nullable
    private String completeCondition;
    /** 说明横幅 */
    @Nullable
    private String noticeText;
    /** 标记完成对话框正文 */
    @Nullable
    private String markDoneTip;
    /** REGISTER / MANUAL / AUTO */
    @Nullable
    private String completeWay;
    @Nullable
    private String completeWayText;
    @Nullable
    private String completedAt;
    @Nullable
    private UserBriefVO completedBy;
    @Nullable
    private String ignoredAt;
    @Nullable
    private UserBriefVO ignoredBy;
    @Nullable
    private RelatedBatchVO relatedBatch;
    private boolean canRegister;
    /** 员工为 false */
    private boolean canComplete;
    /** 员工为 false */
    private boolean canIgnore;

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    @Nullable
    public String getSourceModuleName() {
        return sourceModuleName;
    }

    @Nullable
    public String getDueText() {
        return dueText;
    }

    @Nullable
    public String getDueHint() {
        return dueHint;
    }

    @Nullable
    public String getReceiversText() {
        return receiversText;
    }

    @Nullable
    public String getReceiversHint() {
        return receiversHint;
    }

    @Nullable
    public String getStatusNote() {
        return statusNote;
    }

    @Nullable
    public String getDetailLabel() {
        return detailLabel;
    }

    @Nullable
    public String getDetailValue() {
        return detailValue;
    }

    public int getRemindCount() {
        return remindCount;
    }

    @Nullable
    public String getRemindText() {
        return remindText;
    }

    @Nullable
    public String getCompleteCondition() {
        return completeCondition;
    }

    @Nullable
    public String getNoticeText() {
        return noticeText;
    }

    @Nullable
    public String getMarkDoneTip() {
        return markDoneTip;
    }

    @Nullable
    public String getCompleteWay() {
        return completeWay;
    }

    @Nullable
    public String getCompleteWayText() {
        return completeWayText;
    }

    @Nullable
    public String getCompletedAt() {
        return completedAt;
    }

    @Nullable
    public UserBriefVO getCompletedBy() {
        return completedBy;
    }

    @Nullable
    public String getIgnoredAt() {
        return ignoredAt;
    }

    @Nullable
    public UserBriefVO getIgnoredBy() {
        return ignoredBy;
    }

    @Nullable
    public RelatedBatchVO getRelatedBatch() {
        return relatedBatch;
    }

    public boolean isCanRegister() {
        return canRegister;
    }

    public boolean isCanComplete() {
        return canComplete;
    }

    public boolean isCanIgnore() {
        return canIgnore;
    }
}
