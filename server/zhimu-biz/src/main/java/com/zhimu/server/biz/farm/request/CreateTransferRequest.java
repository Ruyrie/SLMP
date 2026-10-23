package com.zhimu.server.biz.farm.request;

import lombok.Data;

/** 发起转让（API-F29）：受让场长的 member_id */
@Data
public class CreateTransferRequest {
    private Long toMemberId;
}
