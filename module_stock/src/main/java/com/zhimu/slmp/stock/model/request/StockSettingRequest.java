package com.zhimu.slmp.stock.model.request;

/** API-M41 临期天数（1–365，首次 version 为 0）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockSettingRequest {
    public Integer nearDaysVet;
    public Integer nearDaysVaccine;
    public Integer nearDaysDisinfectant;
    public Integer version;
}
