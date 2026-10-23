package com.zhimu.slmp.finance.model.request;

/**
 * API-E07 作废手工记录
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class VersionRequest {

    private final int version;

    public VersionRequest(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }
}
