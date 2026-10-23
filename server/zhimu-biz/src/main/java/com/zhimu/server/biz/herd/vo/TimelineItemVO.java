package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import lombok.Data;

import java.util.List;

/** API-L16 生产记录时间线项（L06）。 */
@Data
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

    private Long id;
    /** 转栏、死淘记录的 biz_id 即 event_id */
    private Long bizId;
    private String recordType;
    private String bizType;
    private String bizDate;
    private String occurredAt;
    private String title;
    private Boolean isToday;
    private Boolean expandable;
    /** EDIT / CHANGE_REQUEST / NONE */
    private String action;
    private String subtitle;
    private String operatorName;
    private String route;
    private String actionNote;
    private List<KeyValue> detail;
    private ChangeRequestStateVO changeState;

    /** 键值对（时间线展开详情） */
    @Data
    public static class KeyValue {
        private String key;
        private String value;
    }
}
