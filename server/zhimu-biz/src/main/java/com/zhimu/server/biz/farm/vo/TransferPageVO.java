package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.util.List;

/** 转让页数据（F28） */
@Data
public class TransferPageVO {
    private TransferFarmVO farm;
    private List<TransferCandidateVO> candidates;
    private TransferVO pending;
}
