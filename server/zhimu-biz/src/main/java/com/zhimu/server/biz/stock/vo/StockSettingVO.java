package com.zhimu.server.biz.stock.vo;

import lombok.Data;

/** API-M40 / M41 临期天数设置（App {@code StockSettingVO}）。 */
@Data
public class StockSettingVO {

    private int nearDaysVet;
    private int nearDaysVaccine;
    private int nearDaysDisinfectant;
    private int version;
}
