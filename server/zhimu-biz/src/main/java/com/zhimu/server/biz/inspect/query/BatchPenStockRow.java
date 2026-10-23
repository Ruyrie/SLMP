package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** 批次在某栏舍的在栏数（只读 t_batch_pen）。 */
@Data
public class BatchPenStockRow {
    private Long batchId;
    private Long penId;
    private Integer stockCount;
}
