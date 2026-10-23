package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** 批次所在栏舍及在栏数（BatchListItemVO、BatchDetailVO、TransferResultVO 共用）。 */
@Data
public class BatchPenItem {

    private Long penId;
    private String penName;
    private Integer stockCount;
}
