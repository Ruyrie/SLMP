package com.zhimu.server.biz.stock.query;

/** 栏舍内某批次的在栏数（t_batch_pen 只读投影）。 */
public class PenBatchRow {

    private Long batchId;
    private Integer stockCount;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Integer getStockCount() {
        return stockCount;
    }

    public void setStockCount(Integer stockCount) {
        this.stockCount = stockCount;
    }
}
