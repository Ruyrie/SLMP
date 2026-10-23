package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 转让请求（F29、F32，FarmDetailVO.pending_transfer、TransferPageVO.pending） */
@Data
public class TransferVO {
    private Long transferId;
    private Long farmId;
    private Long toMemberId;
    private String farmName;
    private String fromUserName;
    private String toName;
    private String status;
    private String statusText;
    private String createdAt;
    private String expiresAt;
    private String handledAt;
    private String cancelReason;
}
