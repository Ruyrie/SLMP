package com.zhimu.server.biz.stock.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhimu.server.biz.stock.vo.StockFlowItemVO;
import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** API-M01 饲料库存概览（App {@code FeedStockOverviewVO}）。 */
@Data
public class FeedStockOverviewVO {

    private Banner banner;
    private int feedCount;
    private int warnCount;
    @ManagerView
    private BigDecimal stockAmount;
    @JsonProperty("usage_7d_total")
    private BigDecimal usage7dTotal;
    @JsonProperty("usage_7d_items")
    private List<UsageItem> usage7dItems = new ArrayList<>();
    private List<StockFlowItemVO> recentFlows = new ArrayList<>();
    private boolean canOtherOut;
    private boolean hasFeed;
    private boolean farmReadonly;

    /** 预警横幅：kind = DAYS 按天数 / MIN 按下限 / MULTI 多种饲料同时不足 */
    @Data
    public static class Banner {
        private String kind;
        private Long feedId;
        private String feedName;
        private Integer remainingDays;
        private Integer warnDays;
        private QtyVO stock;
        private BigDecimal minStock;
        private List<String> feedNames;
        private Long preselectFeedId;
    }

    @Data
    public static class UsageItem {
        private long feedId;
        private String feedName;
        private BigDecimal qty;
    }
}
