package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.StockSettingRequest;
import com.zhimu.server.biz.stock.vo.StockSettingVO;

/** 库存设置：临期天数（API-M40 / M41）。 */
public interface StockSettingService {

    /** API-M40 读取临期天数；没有记录时返回默认 30 天、version 0 */
    StockSettingVO get();

    /** API-M41 保存临期天数；version 不一致抛 40902 */
    StockSettingVO save(StockSettingRequest request);

    /** 某药品类别的临期天数（VET_DRUG / VACCINE / DISINFECTANT），其他类别按兽药 */
    int nearDays(String category);

    StockSettingVO current();
}
