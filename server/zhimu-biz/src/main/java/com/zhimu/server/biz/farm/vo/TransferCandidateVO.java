package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 转让候选场长（TransferPageVO.candidates） */
@Data
public class TransferCandidateVO {
    private Long memberId;
    private Long userId;
    private String name;
    private String nameInitial;
    private String phoneMasked;
}
