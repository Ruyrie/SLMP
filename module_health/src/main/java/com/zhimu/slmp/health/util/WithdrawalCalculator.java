package com.zhimu.slmp.health.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 休药期规则（服务端 2.7 节口径，App 只做界面预估，以接口返回为准）：
 * <ul>
 *     <li>末次用药日 = 首次用药日期 + 疗程 − 1（10月5日、5 天 → 10月9日）</li>
 *     <li>该药品的到期日 = 末次用药日 + 休药天数（10月9日 + 20 → 10月29日）</li>
 *     <li>批次可出栏日期 = 各药品到期日取最晚的一天；没有记录为空</li>
 *     <li>状态：可出栏日期为空 NONE；今天早于可出栏日期 IN_WITHDRAWAL（剩余天数 = 可出栏日期 − 今天）；可出栏日期当天起 ENDED</li>
 *     <li>进度 = (今天 − 决定项末次) ÷ 决定项休药天数，限 0–1；休药天数为 0 或已到期为 1</li>
 * </ul>
 */
public final class WithdrawalCalculator {

    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_IN_WITHDRAWAL = "IN_WITHDRAWAL";
    public static final String STATUS_ENDED = "ENDED";

    private WithdrawalCalculator() {
    }

    /** 一次用药（末次用药日与休药天数） */
    public static final class DrugUse {
        @NonNull
        public final LocalDate lastDate;
        public final int withdrawalDays;

        public DrugUse(@NonNull LocalDate lastDate, int withdrawalDays) {
            this.lastDate = lastDate;
            this.withdrawalDays = withdrawalDays;
        }

        @NonNull
        public LocalDate endDate() {
            return releaseDate(lastDate, withdrawalDays);
        }
    }

    @NonNull
    public static LocalDate lastDate(@NonNull LocalDate firstDate, int courseDays) {
        return firstDate.plusDays(Math.max(courseDays, 1) - 1L);
    }

    @NonNull
    public static LocalDate releaseDate(@NonNull LocalDate lastDate, int withdrawalDays) {
        return lastDate.plusDays(Math.max(withdrawalDays, 0));
    }

    /** 决定可出栏日期的一项（到期日最晚；同日取末次用药较晚的一项）；没有用药时返回 null */
    @Nullable
    public static DrugUse decisive(@Nullable List<DrugUse> uses) {
        DrugUse best = null;
        if (uses == null) {
            return null;
        }
        for (DrugUse u : uses) {
            if (best == null || u.endDate().isAfter(best.endDate())
                    || (u.endDate().equals(best.endDate()) && u.lastDate.isAfter(best.lastDate))) {
                best = u;
            }
        }
        return best;
    }

    /** 批次可出栏日期：各药品到期日取最晚；没有用药返回 null */
    @Nullable
    public static LocalDate batchReleaseDate(@Nullable List<DrugUse> uses) {
        DrugUse d = decisive(uses);
        return d == null ? null : d.endDate();
    }

    @NonNull
    public static String status(@Nullable LocalDate releaseDate, @NonNull LocalDate today) {
        if (releaseDate == null) {
            return STATUS_NONE;
        }
        return today.isBefore(releaseDate) ? STATUS_IN_WITHDRAWAL : STATUS_ENDED;
    }

    /** 剩余天数（已到期为 0） */
    public static int daysLeft(@Nullable LocalDate releaseDate, @NonNull LocalDate today) {
        if (releaseDate == null) {
            return 0;
        }
        long d = ChronoUnit.DAYS.between(today, releaseDate);
        return (int) Math.max(d, 0);
    }

    /** 休药进度 0–1 */
    public static double progress(@Nullable LocalDate decisiveLastDate, int withdrawalDays, @NonNull LocalDate today) {
        if (decisiveLastDate == null || withdrawalDays <= 0) {
            return 1d;
        }
        long passed = ChronoUnit.DAYS.between(decisiveLastDate, today);
        double p = (double) passed / withdrawalDays;
        return Math.max(0d, Math.min(1d, p));
    }

    /** 某药品的标签：决定项 LATEST（休药已结束时为 ENDED）；到期日不晚于今天 ENDED；其余 NONE */
    @NonNull
    public static String tagOf(@NonNull DrugUse use, @Nullable DrugUse decisive, @NonNull LocalDate today) {
        boolean ended = !use.endDate().isAfter(today);
        if (decisive != null && use.endDate().equals(decisive.endDate())) {
            return ended ? "ENDED" : "LATEST";
        }
        return ended ? "ENDED" : "NONE";
    }
}
