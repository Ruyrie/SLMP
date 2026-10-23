package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

/** 打开消息的结果（API-T15）：去向、失效与无权说明、最新未读数。 */
@Getter
@Setter
public class OpenResultVO {

    /** OK / DELETED / NO_PERMISSION / NOT_MEMBER / FARM_FROZEN / NO_JUMP */
    private String targetStatus;
    private String targetStatusText;
    private LinkVO link;
    /** BATCH / DIAGNOSIS / SALE_ORDER / LOT / TODO / CHANGE_REQUEST */
    private String deletedRecordType;
    private String pageTitle;
    private String deletedTitle;
    private String deletedDesc;
    private String denyTitle;
    private String denyDesc;
    private Integer totalUnread;
}
