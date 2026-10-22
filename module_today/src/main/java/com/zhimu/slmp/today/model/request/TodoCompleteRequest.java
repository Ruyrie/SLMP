package com.zhimu.slmp.today.model.request;

/**
 * 手动标记完成（API-T08）。
 */
public class TodoCompleteRequest {

    private final int version;

    public TodoCompleteRequest(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }
}
