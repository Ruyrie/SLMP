package com.zhimu.slmp.stock.model.vo;

/** API-M42 提交结果：DONE / PROCESSING / NOT_FOUND；字段直接公开，由 Gson 按下划线命名读写。 */
public class RequestStatusVO {
    public String status;
    public String bizType;
    public Long bizId;
}
