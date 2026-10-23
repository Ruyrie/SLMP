package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.enums.StockCodes;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 饲料统计口径（模块09 2.7 节）：剩余可用天数、库存不足判定。
 * <p>
 * 日用量优先取模块06 饲喂方案（remaining_basis = PLAN，本模块暂未接入，读取库中缓存值）；
 * 否则取近 7 天日均 = usage_7d_qty ÷ usage_days；都没有时为空（NONE，显示“暂无消耗”）。
 * 剩余天数 = 结存 ÷ 日用量，向下取整；结存为 0 时为 0。
 * 库存不足：EMPTY 结存为 0 → BELOW_MIN 小于下限 → BELOW_DAYS 剩余天数小于预警天数（同时满足时优先 DAYS）。
 */
public final class FeedStats {

    private FeedStats() {
    }

    /**
     * @param dailyUseQty  日用量
     * @param remainingDays 剩余可用天数
     * @param basis        口径 PLAN / USAGE_7D / NONE
     * @param lowStock     是否库存不足
     * @param reason       EMPTY / BELOW_MIN / BELOW_DAYS / null
     */
    public record View(BigDecimal dailyUseQty, Integer remainingDays, String basis, boolean lowStock,
                       String reason) {
    }

    public static View of(Feed feed) {
        BigDecimal stock = StockQty.zeroIfNull(feed.getStockQty());
        BigDecimal daily = dailyUse(feed);
        String basis = daily != null && daily.signum() > 0
                ? (StockCodes.BASIS_PLAN.equals(feed.getRemainingBasis()) ? StockCodes.BASIS_PLAN
                : StockCodes.BASIS_USAGE_7D)
                : StockCodes.BASIS_NONE;
        Integer remaining = remainingDays(stock, daily);
        String reason = lowStockReason(feed, stock, remaining);
        return new View(daily, remaining, basis, reason != null, reason);
    }

    /** 日用量：有缓存值用缓存值，否则按近 7 天领用 ÷ 天数（首次领用就在今天时为空） */
    public static BigDecimal dailyUse(Feed feed) {
        if (feed.getDailyUseQty() != null) {
            return feed.getDailyUseQty();
        }
        BigDecimal usage = StockQty.zeroIfNull(feed.getUsage7dQty());
        int days = feed.getUsageDays() == null ? 0 : feed.getUsageDays();
        if (days <= 0) {
            return null;
        }
        return usage.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    public static Integer remainingDays(BigDecimal stock, BigDecimal daily) {
        if (stock.signum() == 0) {
            return 0;
        }
        if (daily == null || daily.signum() <= 0) {
            return null;
        }
        return stock.divide(daily, 0, RoundingMode.DOWN).intValue();
    }

    private static String lowStockReason(Feed feed, BigDecimal stock, Integer remaining) {
        if (stock.signum() == 0) {
            return StockCodes.LOW_EMPTY;
        }
        BigDecimal min = feed.getMinStock();
        if (min != null && stock.compareTo(min) < 0) {
            return StockCodes.LOW_BELOW_MIN;
        }
        Integer warnDays = feed.getWarnDays() == null ? 7 : feed.getWarnDays();
        if (remaining != null && remaining < warnDays) {
            return StockCodes.LOW_BELOW_DAYS;
        }
        return null;
    }

    /** 预警横幅优先 DAYS，其次 MIN */
    public static String bannerKind(String reason) {
        if (StockCodes.LOW_BELOW_DAYS.equals(reason)) {
            return StockCodes.BANNER_DAYS;
        }
        if (StockCodes.LOW_BELOW_MIN.equals(reason) || StockCodes.LOW_EMPTY.equals(reason)) {
            return StockCodes.BANNER_MIN;
        }
        return null;
    }
}
