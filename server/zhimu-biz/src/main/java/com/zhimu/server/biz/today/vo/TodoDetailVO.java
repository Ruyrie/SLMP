package com.zhimu.server.biz.today.vo;

import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Getter;
import lombok.Setter;

/** 待办详情（API-T07–T09 data）。 */
@Getter
@Setter
public class TodoDetailVO extends TodoItemVO {

    private String subtitle;
    private String sourceModuleName;
    private String dueText;
    private String dueHint;
    private String receiversText;
    private String receiversHint;
    private String statusNote;
    private String detailLabel;
    private String detailValue;
    private Integer remindCount;
    private String remindText;
    private String completeCondition;
    private String noticeText;
    private String markDoneTip;
    private String completeWay;
    private String completeWayText;
    private String completedAt;
    private UserBriefVO completedBy;
    private String ignoredAt;
    private UserBriefVO ignoredBy;
    private RelatedBatchVO relatedBatch;
    private Boolean canRegister;
    private Boolean canComplete;
    private Boolean canIgnore;
}
