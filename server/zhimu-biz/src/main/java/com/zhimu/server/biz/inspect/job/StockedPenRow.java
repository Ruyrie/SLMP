package com.zhimu.server.biz.inspect.job;

import lombok.Data;

import java.time.LocalDate;

/** 有在栏数的栏舍与其中的在养批次（一栏多批次时多行）。 */
@Data
public class StockedPenRow {

    private Long penId;
    private String penName;
    private Long batchId;
    private String batchNo;
    private LocalDate entryDate;
    private Integer stockCount;
}
