package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.math.BigDecimal;

/** API-L23、L24 死淘结果；message 为“已登记死淘 10 头，存栏已更新”。 */
@Data
public class DeathResultVO {

    private Long eventId;
    private Long batchId;
    private Integer quantity;
    private Integer currentCount;
    private Integer deathCount;
    private BigDecimal mortalityRate;
    private String batchStatus;
    private String message;
    private Boolean batchEnded;
}
