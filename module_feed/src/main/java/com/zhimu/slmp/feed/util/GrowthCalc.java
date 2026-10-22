package com.zhimu.slmp.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.feed.model.vo.StdPointVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 称重即时计算（与服务端一致，提交以后端为准）：平均体重（2 位）、标准体重插值（相邻两点直线插值保留 1 位，
 * 超出首末点为无标准）、偏离 =（平均 − 标准）÷ 标准（4 位）、生长预判（|偏离| 大于允许值时偏慢 / 偏快）、
 * 日增重 =（本次平均 − 基准体重）÷ 相隔天数（2 位）、建议抽样数 = 存栏 × 抽样比例向上取整。
 */
public final class GrowthCalc {

    public static final String SLOW = "SLOW";
    public static final String FAST = "FAST";
    public static final String NORMAL = "NORMAL";
    public static final String NO_STD = "NO_STD";

    private GrowthCalc() {
    }

    /** 平均体重：总重 ÷ 头数，2 位小数；头数为 0 或缺少数据时为 null */
    @Nullable
    public static BigDecimal average(@Nullable BigDecimal total, @Nullable Integer count) {
        if (total == null || count == null || count <= 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /** 逐头合计 */
    @NonNull
    public static BigDecimal sum(@NonNull List<BigDecimal> weights) {
        BigDecimal s = BigDecimal.ZERO;
        for (BigDecimal w : weights) {
            if (w != null) {
                s = s.add(w);
            }
        }
        return s;
    }

    /** 标准体重：相邻两点直线插值，保留 1 位；日龄超出首末点或没有点时为 null */
    @Nullable
    public static BigDecimal interpolate(@Nullable List<StdPointVO> points, int dayAge) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        List<StdPointVO> sorted = new ArrayList<>();
        for (StdPointVO p : points) {
            if (p != null && p.getStdWeight() != null) {
                sorted.add(p);
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        Collections.sort(sorted, Comparator.comparingInt(StdPointVO::getDayAge));
        StdPointVO first = sorted.get(0);
        StdPointVO last = sorted.get(sorted.size() - 1);
        if (dayAge < first.getDayAge() || dayAge > last.getDayAge()) {
            return null;
        }
        for (int i = 0; i < sorted.size(); i++) {
            StdPointVO p = sorted.get(i);
            if (p.getDayAge() == dayAge) {
                return p.getStdWeight().setScale(1, RoundingMode.HALF_UP);
            }
            if (p.getDayAge() > dayAge && i > 0) {
                StdPointVO a = sorted.get(i - 1);
                BigDecimal span = BigDecimal.valueOf(p.getDayAge() - a.getDayAge());
                BigDecimal offset = BigDecimal.valueOf(dayAge - a.getDayAge());
                BigDecimal v = a.getStdWeight().add(p.getStdWeight().subtract(a.getStdWeight())
                        .multiply(offset).divide(span, 6, RoundingMode.HALF_UP));
                return v.setScale(1, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    /** 偏离标准：（平均 − 标准）÷ 标准，4 位 */
    @Nullable
    public static BigDecimal deviation(@Nullable BigDecimal avg, @Nullable BigDecimal std) {
        if (avg == null || std == null || std.signum() == 0) {
            return null;
        }
        return avg.subtract(std).divide(std, 4, RoundingMode.HALF_UP);
    }

    /** 是否超出允许偏差（|偏离| 大于允许值，等于不算） */
    public static boolean outOfRange(@Nullable BigDecimal deviation, @Nullable BigDecimal allowed) {
        return deviation != null && allowed != null && deviation.abs().compareTo(allowed) > 0;
    }

    /** 生长预判：没有标准为 NO_STD；超出允许偏差时偏慢 SLOW / 偏快 FAST，否则 NORMAL；还没有平均体重时为 null */
    @Nullable
    public static String growthStatus(@Nullable BigDecimal avg, @Nullable BigDecimal std, @Nullable BigDecimal allowed) {
        if (std == null) {
            return avg == null ? null : NO_STD;
        }
        BigDecimal dev = deviation(avg, std);
        if (dev == null) {
            return null;
        }
        if (!outOfRange(dev, allowed)) {
            return NORMAL;
        }
        return dev.signum() < 0 ? SLOW : FAST;
    }

    /** 日增重：（本次平均 − 基准体重）÷ 相隔天数，2 位；缺少基准或天数不大于 0 时为 null */
    @Nullable
    public static BigDecimal dailyGain(@Nullable BigDecimal avg, @Nullable BigDecimal baseWeight,
                                       @Nullable String weighDate, @Nullable String baseDate) {
        LocalDate d = DateFormatter.parseDate(weighDate);
        LocalDate b = DateFormatter.parseDate(baseDate);
        if (avg == null || baseWeight == null || d == null || b == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(b, d);
        if (days <= 0) {
            return null;
        }
        return avg.subtract(baseWeight).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    /** 改日期时的日龄预算：入栏日龄 +（日期 − 入栏日期） */
    @Nullable
    public static Integer estimateDayAge(@Nullable Integer entryAgeDays, @Nullable String entryDate,
                                         @Nullable String date) {
        LocalDate e = DateFormatter.parseDate(entryDate);
        LocalDate d = DateFormatter.parseDate(date);
        if (entryAgeDays == null || e == null || d == null) {
            return null;
        }
        return entryAgeDays + (int) ChronoUnit.DAYS.between(e, d);
    }

    /** 建议抽样数：存栏 × 抽样比例，向上取整 */
    @Nullable
    public static Integer suggestedSample(@Nullable Integer stock, @Nullable BigDecimal ratio) {
        if (stock == null || ratio == null) {
            return null;
        }
        return BigDecimal.valueOf(stock).multiply(ratio).setScale(0, RoundingMode.CEILING).intValue();
    }

    /**
     * 结果卡底部说明：偏慢 / 偏快时说明会被标记；回到范围内而批次原有标记时说明会取消；其余“在允许范围内”。
     *
     * @param currentStatus 批次当前生长标记（WeighFormVO.current_growth_status）
     */
    @Nullable
    public static String predictNote(@Nullable String status, @Nullable String currentStatus) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case SLOW:
                return "提交后批次标为生长偏慢，下次称重回到范围内自动取消";
            case FAST:
                return "提交后批次标为生长偏快，下次称重回到范围内自动取消";
            case NO_STD:
                return "平台还没有这个品种的标准体重，只记录实际体重";
            default:
                if (SLOW.equals(currentStatus)) {
                    return "提交后取消生长偏慢标记";
                }
                if (FAST.equals(currentStatus)) {
                    return "提交后取消生长偏快标记";
                }
                return "在允许范围内";
        }
    }

    /** 生长标签文字：偏慢 / 偏快，其余为 null（不显示标签） */
    @Nullable
    public static String statusTag(@Nullable String status) {
        if (SLOW.equals(status)) {
            return "生长偏慢";
        }
        if (FAST.equals(status)) {
            return "生长偏快";
        }
        return null;
    }
}
