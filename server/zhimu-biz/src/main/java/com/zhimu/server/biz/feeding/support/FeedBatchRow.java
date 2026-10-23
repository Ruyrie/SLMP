package com.zhimu.server.biz.feeding.support;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 模块04 批次只读行：补 BatchQueryService 未提供的入栏均重与已结束批次列表。 */
@Data
public class FeedBatchRow {
    private Long batchId;
    private String batchNo;
    private Long breedId;
    private Long categoryId;
    private LocalDate entryDate;
    private Integer entryAgeDays;
    private Integer entryCount;
    private BigDecimal entryAvgWeight;
    private Integer currentCount;
    private String status;
}
