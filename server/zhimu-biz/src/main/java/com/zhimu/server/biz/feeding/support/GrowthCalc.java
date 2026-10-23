package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.vo.WeighVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 称重与生长计算（技术准备文档 2.7.6、2.7.7）。 */
public final class GrowthCalc {

    private GrowthCalc() {
    }

    /** 标准体重按日龄点直线插值，保留 1 位；超出首末点返回 null（不外推） */
    public static BigDecimal interpolate(List<WeighVO.StdPoint> points, Integer dayAge) {
        if (points == null || points.isEmpty() || dayAge == null) {
            return null;
        }
        WeighVO.StdPoint first = points.get(0);
        WeighVO.StdPoint last = points.get(points.size() - 1);
        if (dayAge < first.getDayAge() || dayAge > last.getDayAge()) {
            return null;
        }
        for (int i = 0; i < points.size(); i++) {
            WeighVO.StdPoint current = points.get(i);
            if (current.getDayAge().equals(dayAge)) {
                return current.getStdWeight();
            }
            if (i + 1 < points.size()) {
                WeighVO.StdPoint next = points.get(i + 1);
                if (dayAge > current.getDayAge() && dayAge < next.getDayAge()) {
                    BigDecimal perDay = next.getStdWeight().subtract(current.getStdWeight())
                            .divide(BigDecimal.valueOf(next.getDayAge() - current.getDayAge()), 6, RoundingMode.HALF_UP);
                    return current.getStdWeight()
                            .add(perDay.multiply(BigDecimal.valueOf(dayAge - current.getDayAge())))
                            .setScale(1, RoundingMode.HALF_UP);
                }
            }
        }
        return null;
    }

    /** 偏离 =（平均体重 − 标准体重）÷ 标准体重，保留 4 位 */
    public static BigDecimal deviation(BigDecimal avgWeight, BigDecimal stdWeight) {
        if (avgWeight == null || stdWeight == null || stdWeight.signum() == 0) {
            return null;
        }
        return avgWeight.subtract(stdWeight).divide(stdWeight, 4, RoundingMode.HALF_UP);
    }

    /** 生长结果：偏离小于负偏差为 SLOW，大于正偏差为 FAST，否则 NORMAL；无标准为 NO_STD */
    public static String growthResult(BigDecimal deviationRatio, BigDecimal allowedDeviation) {
        if (deviationRatio == null || allowedDeviation == null) {
            return "NO_STD";
        }
        if (deviationRatio.compareTo(allowedDeviation.negate()) < 0) {
            return "SLOW";
        }
        if (deviationRatio.compareTo(allowedDeviation) > 0) {
            return "FAST";
        }
        return "NORMAL";
    }

    /** 日增重 =（本次平均 − 基准）÷ 相隔天数，保留 2 位 */
    public static BigDecimal dailyGain(BigDecimal avgWeight, BigDecimal baseWeight, long days) {
        if (avgWeight == null || baseWeight == null || days <= 0) {
            return null;
        }
        return avgWeight.subtract(baseWeight)
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    /** 建议抽样数 = 存栏 × 15%，向上取整 */
    public static int suggestedSample(Integer stockCount) {
        int stock = stockCount == null ? 0 : stockCount;
        return BigDecimal.valueOf(stock).multiply(new BigDecimal("0.15"))
                .setScale(0, RoundingMode.CEILING).intValue();
    }

    /** 称重间隔 = 28 ÷ 次数取整（1→28、2→14、3→9、4→7） */
    public static int intervalDays(Integer perMonth) {
        int times = perMonth == null || perMonth <= 0 ? 2 : perMonth;
        return 28 / times;
    }

    public static BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round4(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal round1(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }
}
