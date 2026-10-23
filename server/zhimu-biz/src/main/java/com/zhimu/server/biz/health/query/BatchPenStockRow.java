package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** 批次在各栏舍的在栏数（t_batch_pen）：主栏舍、按栏舍登记用药、员工范围判断。 */
@Getter
@Setter
public class BatchPenStockRow {
    private Long batchId;
    private Long penId;
    private Integer stockCount;
}
