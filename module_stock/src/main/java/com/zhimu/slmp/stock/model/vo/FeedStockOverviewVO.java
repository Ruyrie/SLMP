package com.zhimu.slmp.stock.model.vo;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.List;

/** API-M01 饲料库存概览（M01）；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedStockOverviewVO {
    public Banner banner;
    public int feedCount;
    public int warnCount;
    public BigDecimal stockAmount;
    @SerializedName("usage_7d_total")
    public BigDecimal usage7dTotal;
    @SerializedName("usage_7d_items")
    public List<UsageItem> usage7dItems;
    public List<StockFlowItemVO> recentFlows;
    public boolean canOtherOut;
    public boolean hasFeed;
    public boolean farmReadonly;

    public static class Banner {
        public String kind;
        public Long feedId;
        public String feedName;
        public Integer remainingDays;
        public Integer warnDays;
        public QtyVO stock;
        public BigDecimal minStock;
        public List<String> feedNames;
        public Long preselectFeedId;
    }

    public static class UsageItem {
        public long feedId;
        public String feedName;
        public BigDecimal qty;
    }
}
