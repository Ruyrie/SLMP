package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** L02 栏舍卡中的在栏批次。休药信息待模块08接入。 */
@Data
public class MyPenBatchVO {

    private Long batchId;
    private String batchNo;
    private String breedTitle;
    private Integer qtyInPen;
    private Integer dayAge;
    private Boolean inWithdrawal;
    private String stageName;
    private Integer withdrawalRemainingDays;
}
