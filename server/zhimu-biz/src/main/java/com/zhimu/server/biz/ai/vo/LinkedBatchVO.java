package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 可关联 / 已关联的在栏批次（API-K02、K05）。
 */
@Data
public class LinkedBatchVO {

    private Long batchId;
    private String batchNo;
    private String categoryName;
    private String stageName;
    private String breedName;
    private Long penId;
    private String penName;
    private int dayAge;
    private int currentCount;
    private String countUnitText;
    private BigDecimal latestAvgWeight;
    private LocalDate latestWeighDate;
}
