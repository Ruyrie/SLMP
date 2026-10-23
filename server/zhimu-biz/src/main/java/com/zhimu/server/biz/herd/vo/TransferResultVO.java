package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L21、L22 转栏结果；message 为“已转栏 100 头到 4 号育肥舍”。 */
@Data
public class TransferResultVO {

    private Long eventId;
    private Long batchId;
    private Integer quantity;
    private Integer overCapacityCount;
    private String message;
    private BatchPenItem fromPen;
    private BatchPenItem toPen;
}
