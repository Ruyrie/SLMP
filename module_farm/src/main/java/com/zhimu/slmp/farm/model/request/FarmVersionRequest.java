package com.zhimu.slmp.farm.model.request;

/**
 * 停用、重新启用（API-F11、F12）：version 取 basic.version。
 */
public class FarmVersionRequest {

    private final int version;

    public FarmVersionRequest(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }
}
