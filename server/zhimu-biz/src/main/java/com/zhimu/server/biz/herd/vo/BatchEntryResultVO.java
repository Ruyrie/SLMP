package com.zhimu.server.biz.herd.vo;

import lombok.Data;

/** API-L18 批次入栏结果（L09 显示批次号、二维码与入栏摘要）。 */
@Data
public class BatchEntryResultVO {

    private Long batchId;
    private Long penId;
    private String batchNo;
    private String qrContent;
    private String labelText;
    private String farmName;
    private String penName;
    private String breedName;
    private String purpose;
    private String entryDate;
    private Integer entryCount;
    private Integer entryAgeDays;
    private Integer penStockAfter;
    private Integer capacity;
    private Integer overCapacityCount;
    private String unitText;
}
