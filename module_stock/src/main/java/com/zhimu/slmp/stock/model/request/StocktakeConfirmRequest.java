package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;
import java.util.List;

/** API-M15 确认盘点：只含已录入实盘的饲料；字段直接公开，由 Gson 按下划线命名读写。 */
public class StocktakeConfirmRequest {
    public List<Item> items;

    public static class Item {
        public Long feedId;
        public BigDecimal bookQty;
        public Integer stockVersion;
        public BigDecimal actualQty;
    }
}
