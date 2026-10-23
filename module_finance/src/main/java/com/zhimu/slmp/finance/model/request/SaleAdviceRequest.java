package com.zhimu.slmp.finance.model.request;

/**
 * API-E34 重新生成出栏建议
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SaleAdviceRequest {

    private final long batchId;

    public SaleAdviceRequest(long batchId) {
        this.batchId = batchId;
    }

    public long getBatchId() {
        return batchId;
    }
}
