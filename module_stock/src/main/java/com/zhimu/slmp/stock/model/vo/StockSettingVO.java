package com.zhimu.slmp.stock.model.vo;

/** API-M40 临期天数设置（M05、M08、M09）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockSettingVO {
    public int nearDaysVet;
    public int nearDaysVaccine;
    public int nearDaysDisinfectant;
    public int version;
}
