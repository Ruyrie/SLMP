package com.zhimu.slmp.health.model.request;

/**
 * 只带 version 的请求体（API-D03–D05 入栏前项目、API-D34 启用计划）。
 */
public class VersionRequest {

    public int version;

    public VersionRequest() {
    }

    public VersionRequest(int version) {
        this.version = version;
    }
}
