package com.zhimu.slmp.stock.model.request;

/** API-M11 / M23 作废：只带 version；字段直接公开，由 Gson 按下划线命名读写。 */
public class VoidRequest {
    public Integer version;
}
