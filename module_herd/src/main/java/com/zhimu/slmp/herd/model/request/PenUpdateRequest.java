package com.zhimu.slmp.herd.model.request;

/**
 * API-L12 编辑栏舍（L05 编辑模式）：去掉 pen_code，加 version。
 */
public class PenUpdateRequest {

    public String penName;
    public String penType;
    public long categoryId;
    public int capacity;
    public int version;
}
