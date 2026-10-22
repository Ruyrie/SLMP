package com.zhimu.slmp.herd.model.request;

/**
 * API-L13 停用、API-L27 启用栏舍（L04）。
 */
public class PenStatusRequest {

    public int version;

    public PenStatusRequest() {
    }

    public PenStatusRequest(int version) {
        this.version = version;
    }
}
