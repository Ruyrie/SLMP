package com.zhimu.slmp.finance.model.request;

import java.util.List;

/**
 * API-E28 确认补录并重算
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SupplementConfirmRequest {

    private final List<Long> supplementIds;
    private final int version;

    public SupplementConfirmRequest(List<Long> supplementIds, int version) {
        this.supplementIds = supplementIds;
        this.version = version;
    }

    public List<Long> getSupplementIds() {
        return supplementIds;
    }

    public int getVersion() {
        return version;
    }
}
