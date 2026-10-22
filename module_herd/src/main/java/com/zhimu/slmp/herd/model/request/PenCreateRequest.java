package com.zhimu.slmp.herd.model.request;

/**
 * API-L11 新增栏舍（L05）。pen_code 提交前转大写。
 */
public class PenCreateRequest {

    public String penCode;
    public String penName;
    public String penType;
    public long categoryId;
    public int capacity;
}
