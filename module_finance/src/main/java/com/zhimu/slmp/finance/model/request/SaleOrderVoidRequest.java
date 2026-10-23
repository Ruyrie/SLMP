package com.zhimu.slmp.finance.model.request;

/**
 * API-E21 作废未收款出栏单
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SaleOrderVoidRequest {

    private final String reason;
    private final int version;

    public SaleOrderVoidRequest(String reason, int version) {
        this.reason = reason;
        this.version = version;
    }

    public String getReason() {
        return reason;
    }

    public int getVersion() {
        return version;
    }
}
