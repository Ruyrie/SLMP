package com.zhimu.slmp.stock.model.vo;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.List;

/** 盘点单（API-M15 返回、M16 列表、M17 详情）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StocktakeVO {
    public long stocktakeId;
    public String stocktakeNo;
    public String stocktakeDate;
    public UserBriefVO operator;
    public int itemCount;
    public int gainCount;
    public int lossCount;
    public BigDecimal gainAmount;
    public BigDecimal lossAmount;
    public List<Item> items;
    public StockActionsVO actions;
    public String createdAt;
    public Integer version;

    public static class Item {
        public long itemId;
        public long feedId;
        public String feedName;
        public String unitText;
        public BigDecimal bookQty;
        public BigDecimal actualQty;
        public BigDecimal diffQty;
        public String result;
        public String resultText;
        public BigDecimal avgPrice;
        public BigDecimal diffAmount;
    }
}
