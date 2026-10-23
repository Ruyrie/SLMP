package com.zhimu.slmp.stock.model.vo;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.List;

/** API-M14 盘点表（M04）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StocktakeSheetVO {
    public String stocktakeDate;
    public UserBriefVO operator;
    public List<Item> items;

    public static class Item {
        public long feedId;
        public String feedName;
        public String unitText;
        public BigDecimal bookQty;
        public Integer stockVersion;
        public boolean lowStock;
        public BigDecimal avgPrice;
    }
}
