package com.zhimu.server.admin.base.support;

import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.StdWeightPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 标准体重折算与阶段解析（管理端-03 2.7）：
 * 两点之间按直线折算，保留 1 位小数（四舍五入）；下限 = 标准 × (1 − 偏差)，上限 = 标准 × (1 + 偏差)；
 * 日增重 = 与前一点体重差 ÷ 日龄差，保留 2 位；称重间隔 = 28 ÷ 建议次数取整。
 */
public final class StdCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(28);

    private StdCalculator() {
    }

    /** 日龄点的上下限与日增重计算结果 */
    public record Derived(BigDecimal stdWeight, BigDecimal lower, BigDecimal upper, BigDecimal dailyGain) {
    }

    /** 某日龄的折算结果 */
    public record StandardAt(BigDecimal stdWeight, BigDecimal lower, BigDecimal upper) {
    }

    public static BigDecimal round1(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    public static BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lower(BigDecimal stdWeight, BigDecimal toleranceRate) {
        if (stdWeight == null || toleranceRate == null) {
            return null;
        }
        return round1(stdWeight.multiply(BigDecimal.ONE.subtract(toleranceRate)));
    }

    public static BigDecimal upper(BigDecimal stdWeight, BigDecimal toleranceRate) {
        if (stdWeight == null || toleranceRate == null) {
            return null;
        }
        return round1(stdWeight.multiply(BigDecimal.ONE.add(toleranceRate)));
    }

    /** 日增重 = (本点体重 − 前点体重) ÷ (本点日龄 − 前点日龄)，首点为 null */
    public static BigDecimal dailyGain(List<StdWeightPoint> sortedPoints, int index) {
        if (sortedPoints == null || index <= 0 || index >= sortedPoints.size()) {
            return null;
        }
        StdWeightPoint prev = sortedPoints.get(index - 1);
        StdWeightPoint current = sortedPoints.get(index);
        int days = current.getDayAge() - prev.getDayAge();
        if (days <= 0) {
            return null;
        }
        return round2(current.getStdWeight().subtract(prev.getStdWeight())
                .divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP));
    }

    /** 按日龄排序（不改原列表） */
    public static List<StdWeightPoint> sorted(List<StdWeightPoint> points) {
        List<StdWeightPoint> copy = new ArrayList<>(points == null ? List.of() : points);
        copy.sort(Comparator.comparing(StdWeightPoint::getDayAge));
        return copy;
    }

    /**
     * 两点之间直线折算；日龄超出首末点时返回 null（管理端-03 2.7：不判断偏快偏慢）。
     */
    public static StandardAt interpolate(List<StdWeightPoint> sortedPoints, int dayAge, BigDecimal toleranceRate) {
        if (sortedPoints == null || sortedPoints.isEmpty()) {
            return null;
        }
        StdWeightPoint first = sortedPoints.get(0);
        StdWeightPoint last = sortedPoints.get(sortedPoints.size() - 1);
        if (dayAge < first.getDayAge() || dayAge > last.getDayAge()) {
            return null;
        }
        for (int i = 0; i < sortedPoints.size(); i++) {
            StdWeightPoint current = sortedPoints.get(i);
            if (current.getDayAge() == dayAge) {
                return new StandardAt(round1(current.getStdWeight()), lower(current.getStdWeight(), toleranceRate),
                        upper(current.getStdWeight(), toleranceRate));
            }
            if (i + 1 < sortedPoints.size()) {
                StdWeightPoint next = sortedPoints.get(i + 1);
                if (dayAge > current.getDayAge() && dayAge < next.getDayAge()) {
                    BigDecimal gainPerDay = next.getStdWeight().subtract(current.getStdWeight())
                            .divide(BigDecimal.valueOf(next.getDayAge() - current.getDayAge()), 6, RoundingMode.HALF_UP);
                    BigDecimal std = current.getStdWeight()
                            .add(gainPerDay.multiply(BigDecimal.valueOf(dayAge - current.getDayAge())));
                    BigDecimal rounded = round1(std);
                    return new StandardAt(rounded, lower(rounded, toleranceRate), upper(rounded, toleranceRate));
                }
            }
        }
        return null;
    }

    /** weigh_interval_days = 28 ÷ suggested_weigh_times 取整（2 → 14，3 → 9） */
    public static int weighInterval(Integer suggestedWeighTimes) {
        int times = suggestedWeighTimes == null || suggestedWeighTimes <= 0 ? 2 : suggestedWeighTimes;
        return DAYS_PER_MONTH.divide(BigDecimal.valueOf(times), 0, RoundingMode.DOWN).intValue();
    }

    /** 日龄所在阶段；超出全部阶段或没有饲喂标准返回 null */
    public static FeedStdStage resolveStage(List<FeedStdStage> stages, int dayAge) {
        if (stages == null) {
            return null;
        }
        for (FeedStdStage stage : stages) {
            if (stage.getDayAgeStart() == null || dayAge < stage.getDayAgeStart()) {
                continue;
            }
            if (stage.getDayAgeEnd() == null || dayAge <= stage.getDayAgeEnd()) {
                return stage;
            }
        }
        return null;
    }

    /** 偏差率以百分数显示，round 不作处理；仅用于比较草稿与线上是否一致 */
    public static boolean sameStage(FeedStdStage a, FeedStdStage b) {
        if (a == null || b == null) {
            return a == b;
        }
        return eq(a.getStageName(), b.getStageName())
                && eq(a.getStageShortName(), b.getStageShortName())
                && eq(a.getDayAgeStart(), b.getDayAgeStart())
                && eq(a.getDayAgeEnd(), b.getDayAgeEnd())
                && eq(a.getFeedTypeCode(), b.getFeedTypeCode())
                && eq(a.getSuggestedFeedName(), b.getSuggestedFeedName())
                && eq(a.getDailyFeed() == null ? null : a.getDailyFeed().stripTrailingZeros(),
                b.getDailyFeed() == null ? null : b.getDailyFeed().stripTrailingZeros())
                && eq(a.getMealsPerDay(), b.getMealsPerDay())
                && eq(a.getMealTimes(), b.getMealTimes());
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
