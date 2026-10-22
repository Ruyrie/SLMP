package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** G01 计划用量缺口横幅（API-G01 plan_shortage）：“育肥全价料库存不足。结存 500 千克，今天还需 540 千克，晚餐前需要补货。” */
public class PlanShortageVO {

    private Long feedId;
    private String feedName;
    private BigDecimal stockQty;
    private BigDecimal todayNeedQty;
    private BigDecimal shortQty;
    private String restockBeforeMealName;

    public long getFeedId() {
        return feedId == null ? 0L : feedId;
    }

    @Nullable
    public String getFeedName() {
        return feedName;
    }

    @Nullable
    public BigDecimal getStockQty() {
        return stockQty;
    }

    @Nullable
    public BigDecimal getTodayNeedQty() {
        return todayNeedQty;
    }

    @Nullable
    public BigDecimal getShortQty() {
        return shortQty;
    }

    @Nullable
    public String getRestockBeforeMealName() {
        return restockBeforeMealName;
    }
}
