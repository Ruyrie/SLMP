package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;

import java.util.Collections;
import java.util.List;

/**
 * API-L16 生产记录时间线项（L06）。
 */
public class TimelineItemVO {

    public static final String TYPE_ENTRY = "ENTRY";
    public static final String TYPE_TRANSFER = "TRANSFER";
    public static final String TYPE_DEATH = "DEATH";
    public static final String TYPE_SALE = "SALE";
    public static final String TYPE_FEED = "FEED";
    public static final String TYPE_WEIGH = "WEIGH";
    public static final String TYPE_IMMUNE = "IMMUNE";
    public static final String TYPE_DRUG = "DRUG";
    public static final String TYPE_INSPECT = "INSPECT";

    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_CHANGE_REQUEST = "CHANGE_REQUEST";
    public static final String ACTION_NONE = "NONE";

    private long id;
    private long bizId;
    private String recordType;
    private String bizType;
    private String bizDate;
    private String occurredAt;
    private String title;
    private boolean isToday;
    private boolean expandable;
    private String action;
    private String subtitle;
    private String operatorName;
    private String route;
    private String actionNote;
    private List<KeyValue> detail;
    private ChangeRequestStateVO changeState;

    public long getId() { return id; }

    /** 转栏、死淘记录的 biz_id 即 event_id */
    public long getBizId() { return bizId; }

    public String getRecordType() { return recordType; }

    public String getBizType() { return bizType; }

    public String getBizDate() { return bizDate; }

    public String getOccurredAt() { return occurredAt; }

    public String getTitle() { return title; }

    public boolean isToday() { return isToday; }

    public boolean isExpandable() { return expandable; }

    /** EDIT / CHANGE_REQUEST / NONE */
    public String getAction() { return action; }

    @Nullable
    public String getSubtitle() { return subtitle; }

    @Nullable
    public String getOperatorName() { return operatorName; }

    @Nullable
    public String getRoute() { return route; }

    @Nullable
    public String getActionNote() { return actionNote; }

    public List<KeyValue> getDetail() { return detail == null ? Collections.emptyList() : detail; }

    @Nullable
    public ChangeRequestStateVO getChangeState() { return changeState; }
}
