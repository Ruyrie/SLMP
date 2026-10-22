package com.zhimu.slmp.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.feed.model.request.FeedPlanStageRequest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G03 保存前校验（与 API-G12 规则一致）：键为阶段路径，例如 stages[1].daily_qty_per_head，客户端据此展开出错阶段。
 */
public final class FeedPlanValidator {

    public static final String F_STAGE_NAME = "stage_name";
    public static final String F_DAY_START = "day_age_start";
    public static final String F_DAY_END = "day_age_end";
    public static final String F_FEED = "feed_id";
    public static final String F_DAILY_QTY = "daily_qty_per_head";
    public static final String F_MEALS = "meals_per_day";
    public static final String F_MEAL_TIMES = "meal_times";
    public static final String F_THRESHOLD = "abnormal_threshold";

    public static final String ERR_FEED = "请选择饲料";
    public static final String ERR_QTY_EMPTY = "请填写每头日投喂量";
    public static final String ERR_QTY_POSITIVE = "每头日投喂量要大于 0 千克";
    public static final String ERR_QTY_SCALE = "每头日投喂量最多 3 位小数";
    public static final String ERR_QTY_MAX = "每头日投喂量不能超过 99.999 千克";
    public static final String ERR_TIME_DUP = "投喂时间不能重复";
    public static final String ERR_THRESHOLD = "偏差比例要在 1% 到 100% 之间";

    private static final BigDecimal MAX_DAILY = new BigDecimal("99.999");
    private static final BigDecimal MIN_THRESHOLD = new BigDecimal("0.01");
    private static final Pattern PATH = Pattern.compile("^stages\\[(\\d+)]\\.(\\w+)$");

    private FeedPlanValidator() {
    }

    /** 字段路径：stages[i].field */
    @NonNull
    public static String key(int index, @NonNull String field) {
        return "stages[" + index + "]." + field;
    }

    /** 从字段路径解析阶段序号；不是阶段字段时为 -1 */
    public static int stageIndexOf(@Nullable String key) {
        if (key == null) {
            return -1;
        }
        Matcher m = PATH.matcher(key);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** 偏差比例（0.01–1.00）的错误文案；通过时为 null */
    @Nullable
    public static String checkThreshold(@Nullable BigDecimal ratio) {
        if (ratio == null || ratio.compareTo(MIN_THRESHOLD) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            return ERR_THRESHOLD;
        }
        return null;
    }

    /** 每头日投喂量的错误文案；通过时为 null */
    @Nullable
    public static String checkDailyQty(@Nullable BigDecimal qty) {
        if (qty == null) {
            return ERR_QTY_EMPTY;
        }
        if (qty.signum() <= 0) {
            return ERR_QTY_POSITIVE;
        }
        if (qty.stripTrailingZeros().scale() > 3) {
            return ERR_QTY_SCALE;
        }
        if (qty.compareTo(MAX_DAILY) > 0) {
            return ERR_QTY_MAX;
        }
        return null;
    }

    /**
     * 校验全部阶段。
     *
     * @param currentDayAge 批次当前日龄（须落在某一阶段内），未知时为 null
     * @return 字段路径 → 文案，按阶段顺序
     */
    @NonNull
    public static Map<String, String> validate(@NonNull List<FeedPlanStageRequest> stages,
                                               @Nullable Integer currentDayAge) {
        Map<String, String> e = new LinkedHashMap<>();
        boolean covered = currentDayAge == null;
        for (int i = 0; i < stages.size(); i++) {
            FeedPlanStageRequest s = stages.get(i);
            String name = s.stageName == null ? "" : s.stageName.trim();
            if (name.isEmpty()) {
                e.put(key(i, F_STAGE_NAME), "请填写阶段名称");
            } else if (name.codePointCount(0, name.length()) > 20) {
                e.put(key(i, F_STAGE_NAME), "阶段名称最多 20 个字");
            }
            if (s.dayAgeStart <= 0) {
                e.put(key(i, F_DAY_START), "起始日龄要为正整数");
            }
            boolean last = i == stages.size() - 1;
            if (s.dayAgeEnd == null) {
                if (!last) {
                    e.put(key(i, F_DAY_END), "只有最后一个阶段可以不填结束日龄");
                }
            } else if (s.dayAgeEnd < s.dayAgeStart) {
                e.put(key(i, F_DAY_END), "结束日龄不能小于起始日龄");
            }
            if (i > 0) {
                Integer prevEnd = stages.get(i - 1).dayAgeEnd;
                if (prevEnd != null && s.dayAgeStart != prevEnd + 1) {
                    e.put(key(i, F_DAY_START), "阶段日龄要连续，应从 " + (prevEnd + 1) + " 日龄开始");
                }
            }
            if (currentDayAge != null && currentDayAge >= s.dayAgeStart
                    && (s.dayAgeEnd == null || currentDayAge <= s.dayAgeEnd)) {
                covered = true;
            }
            if (s.feedId <= 0) {
                e.put(key(i, F_FEED), ERR_FEED);
            }
            String qtyError = checkDailyQty(s.dailyQtyPerHead);
            if (qtyError != null) {
                e.put(key(i, F_DAILY_QTY), qtyError);
            }
            if (s.mealsPerDay < 1 || s.mealsPerDay > 4) {
                e.put(key(i, F_MEALS), "餐次要在 1 到 4 餐之间");
            }
            String timeError = checkMealTimes(s.mealTimes, s.mealsPerDay);
            if (timeError != null) {
                e.put(key(i, F_MEAL_TIMES), timeError);
            }
        }
        if (!covered && !stages.isEmpty()) {
            e.put(key(stages.size() - 1, F_DAY_END), "当前日龄 " + currentDayAge + " 天不在任何阶段内");
        }
        return e;
    }

    /** 投喂时间：个数等于餐次、HH:mm、不重复 */
    @Nullable
    public static String checkMealTimes(@Nullable List<String> times, int meals) {
        if (times == null || times.size() != meals) {
            return "投喂时间个数要与餐次一致";
        }
        Set<String> seen = new HashSet<>();
        for (String t : times) {
            if (MealNameResolver.minutesOf(t) < 0) {
                return "请选择投喂时间";
            }
            if (!seen.add(t)) {
                return ERR_TIME_DUP;
            }
        }
        return null;
    }
}
