package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-L06 批次列表 / 下拉（L03 批次视图、L10、L11 批次下拉）。 */
@Data
public class BatchListItemVO {

    private Long batchId;
    private Long breedId;
    private String batchNo;
    private String breedName;
    private String categoryName;
    private String breedTitle;
    private String entryDate;
    private String purpose;
    private String source;
    private String status;
    private List<BatchPenItem> pens;
    private Integer entryAgeDays;
    private Integer dayAge;
    private Integer currentCount;
    private BigDecimal mortalityRate;
    private Boolean inWithdrawal;
    private String stageName;
    private String endDate;
    private String unit;
    private String unitText;
}
