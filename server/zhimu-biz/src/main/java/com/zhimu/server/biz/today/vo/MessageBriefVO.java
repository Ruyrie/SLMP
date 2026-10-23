package com.zhimu.server.biz.today.vo;

import com.zhimu.server.common.vo.FarmBriefVO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 消息中心列表项（API-T12 列表项、API-T18 data）；date_group 为 TODAY / YESTERDAY / EARLIER。 */
@Getter
@Setter
public class MessageBriefVO {

    private Long messageId;
    private String category;
    private String categoryText;
    private String msgType;
    private String level;
    private String levelText;
    private String title;
    private String summary;
    private String tagText;
    private String tagCode;
    private FarmBriefVO farm;
    private String bizType;
    private Long bizId;
    private Long todoId;
    private String route;
    private String webRoute;
    private Boolean read;
    private Boolean revoked;
    private LocalDateTime createdAt;
    private LocalDateTime sortTime;
    private String dateGroup;
    /** PENDING / OVERDUE / DONE / IGNORED / HANDLED / DEFAULT */
    private String iconState;
    private Integer mergeCount;
    private MessageActionVO action;
}
