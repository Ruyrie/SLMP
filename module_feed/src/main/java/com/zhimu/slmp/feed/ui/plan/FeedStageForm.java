package com.zhimu.slmp.feed.ui.plan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.feed.model.request.FeedPlanStageRequest;
import com.zhimu.slmp.feed.model.vo.FeedPlanStageVO;
import com.zhimu.slmp.feed.model.vo.FeedStdApplyVO;
import com.zhimu.slmp.feed.util.FeedCalc;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * G03 方案阶段的可编辑副本：字段同 FeedPlanStageRequest，另含 state、editable、品种标准参考值、日期与汇总文字。
 * “你调整了 n 项”按阶段比较饲料、每头日投喂量、餐次、投喂时间与标准（套用后比较四项；否则以服务端 adjusted_fields 为基础，
 * 每头日投喂量与 std_daily_qty 实时比较）。
 */
public final class FeedStageForm {

    @Nullable
    public Long stageId;
    @Nullable
    public Long stdStageId;
    @NonNull
    public String stageName = "";
    public int dayAgeStart;
    @Nullable
    public Integer dayAgeEnd;
    @NonNull
    public String state = FeedPlanStageVO.STATE_UPCOMING;
    public boolean editable = true;
    @Nullable
    public Long feedId;
    @Nullable
    public String feedName;
    public boolean lowStock;
    @NonNull
    public String dailyQtyText = "";
    @Nullable
    public BigDecimal stdDailyQty;
    public int mealsPerDay = 3;
    @NonNull
    public List<String> mealTimes = new ArrayList<>();
    @Nullable
    public String enterDate;
    @Nullable
    public String execStartDate;
    @Nullable
    public String execEndDate;
    /** 服务端给出的与标准不同的项（未套用时作为基础） */
    @NonNull
    public List<String> serverAdjusted = new ArrayList<>();
    /** 本次套用标准后的参考值；为空时没有在本页套用 */
    @Nullable
    public StdRef stdRef;

    /** 套用标准时的四项参考值 */
    public static final class StdRef {
        @Nullable
        final Long feedId;
        @Nullable
        final BigDecimal dailyQty;
        final int meals;
        @NonNull
        final List<String> times;

        StdRef(@Nullable Long feedId, @Nullable BigDecimal dailyQty, int meals, @NonNull List<String> times) {
            this.feedId = feedId;
            this.dailyQty = dailyQty;
            this.meals = meals;
            this.times = times;
        }
    }

    @NonNull
    public static FeedStageForm from(@NonNull FeedPlanStageVO vo) {
        FeedStageForm f = new FeedStageForm();
        f.stageId = vo.getStageId();
        f.stageName = FeedFormat.orEmpty(vo.getStageName());
        f.dayAgeStart = vo.getDayAgeStart() == null ? 0 : vo.getDayAgeStart();
        f.dayAgeEnd = vo.getDayAgeEnd();
        f.state = vo.getState() == null ? FeedPlanStageVO.STATE_UPCOMING : vo.getState();
        f.editable = vo.isEditable() && !FeedPlanStageVO.STATE_ENDED.equals(f.state);
        f.feedId = vo.getFeedId();
        f.feedName = vo.getFeedName();
        f.lowStock = vo.isLowStock();
        f.dailyQtyText = vo.getDailyQtyPerHead() == null ? "" : plain(vo.getDailyQtyPerHead());
        f.stdDailyQty = vo.getStdDailyQty();
        f.mealsPerDay = vo.getMealsPerDay() == null ? 3 : vo.getMealsPerDay();
        for (FeedPlanStageVO.FeedMealTimeVO m : vo.getMealTimes()) {
            if (m.getFeedTime() != null) {
                f.mealTimes.add(m.getFeedTime());
            }
        }
        Collections.sort(f.mealTimes);
        f.enterDate = vo.getEnterDate();
        f.execStartDate = vo.getExecStartDate();
        f.execEndDate = vo.getExecEndDate();
        f.serverAdjusted = new ArrayList<>(vo.getAdjustedFields());
        return f;
    }

    /**
     * 由品种标准生成阶段：沿用同起始日龄的原阶段 ID；状态按当前日龄推算。
     *
     * @param existing 同起始日龄的原阶段（可空）
     */
    @NonNull
    public static FeedStageForm fromStd(@NonNull FeedStdApplyVO.FeedStdStageVO s, @Nullable FeedStageForm existing,
                                        @Nullable Integer currentDayAge) {
        FeedStageForm f = new FeedStageForm();
        f.stageId = existing == null ? null : existing.stageId;
        f.stdStageId = s.getStdStageId();
        f.stageName = FeedFormat.orEmpty(s.getStageName());
        f.dayAgeStart = s.getDayAgeStart() == null ? 0 : s.getDayAgeStart();
        f.dayAgeEnd = s.getDayAgeEnd();
        f.state = stateOf(f.dayAgeStart, f.dayAgeEnd, currentDayAge);
        f.editable = !FeedPlanStageVO.STATE_ENDED.equals(f.state);
        f.feedId = s.getMatchedFeedId();
        f.feedName = s.getMatchedFeedName();
        f.dailyQtyText = s.getDailyQty() == null ? "" : plain(s.getDailyQty());
        f.stdDailyQty = s.getDailyQty();
        f.mealsPerDay = s.getMealsPerDay() == null ? 3 : s.getMealsPerDay();
        f.mealTimes = new ArrayList<>(s.getMealTimes());
        Collections.sort(f.mealTimes);
        if (existing != null) {
            f.enterDate = existing.enterDate;
            f.execStartDate = existing.execStartDate;
            f.execEndDate = existing.execEndDate;
        }
        f.stdRef = new StdRef(f.feedId, s.getDailyQty(), f.mealsPerDay, new ArrayList<>(f.mealTimes));
        return f;
    }

    @NonNull
    static String stateOf(int start, @Nullable Integer end, @Nullable Integer current) {
        if (current == null) {
            return FeedPlanStageVO.STATE_UPCOMING;
        }
        if (end != null && end < current) {
            return FeedPlanStageVO.STATE_ENDED;
        }
        if (start <= current) {
            return FeedPlanStageVO.STATE_CURRENT;
        }
        return FeedPlanStageVO.STATE_UPCOMING;
    }

    @NonNull
    public FeedStageForm copy() {
        FeedStageForm f = new FeedStageForm();
        f.stageId = stageId;
        f.stdStageId = stdStageId;
        f.stageName = stageName;
        f.dayAgeStart = dayAgeStart;
        f.dayAgeEnd = dayAgeEnd;
        f.state = state;
        f.editable = editable;
        f.feedId = feedId;
        f.feedName = feedName;
        f.lowStock = lowStock;
        f.dailyQtyText = dailyQtyText;
        f.stdDailyQty = stdDailyQty;
        f.mealsPerDay = mealsPerDay;
        f.mealTimes = new ArrayList<>(mealTimes);
        f.enterDate = enterDate;
        f.execStartDate = execStartDate;
        f.execEndDate = execEndDate;
        f.serverAdjusted = new ArrayList<>(serverAdjusted);
        f.stdRef = stdRef;
        return f;
    }

    @NonNull
    public FeedPlanStageRequest toRequest() {
        FeedPlanStageRequest r = new FeedPlanStageRequest();
        r.stageId = stageId;
        r.stdStageId = stdStageId;
        r.stageName = stageName;
        r.dayAgeStart = dayAgeStart;
        r.dayAgeEnd = dayAgeEnd;
        r.feedId = feedId == null ? 0L : feedId;
        r.dailyQtyPerHead = FeedCalc.parse(dailyQtyText);
        r.mealsPerDay = mealsPerDay;
        List<String> times = new ArrayList<>(mealTimes);
        Collections.sort(times);
        r.mealTimes = times;
        return r;
    }

    @Nullable
    public BigDecimal dailyQty() {
        return FeedCalc.parse(dailyQtyText);
    }

    public boolean isCurrent() {
        return FeedPlanStageVO.STATE_CURRENT.equals(state);
    }

    public boolean isEnded() {
        return FeedPlanStageVO.STATE_ENDED.equals(state);
    }

    /** 与标准不同的项数 */
    public int adjustedCount() {
        if (!editable) {
            return 0;
        }
        BigDecimal qty = dailyQty();
        StdRef ref = stdRef;
        if (ref != null) {
            int n = 0;
            if (!Objects.equals(ref.feedId, feedId)) {
                n++;
            }
            if (!sameQty(ref.dailyQty, qty)) {
                n++;
            }
            if (ref.meals != mealsPerDay) {
                n++;
            }
            List<String> sorted = new ArrayList<>(mealTimes);
            Collections.sort(sorted);
            if (!ref.times.equals(sorted)) {
                n++;
            }
            return n;
        }
        int n = 0;
        for (String field : serverAdjusted) {
            if (!"daily_qty".equals(field)) {
                n++;
            }
        }
        if (stdDailyQty != null) {
            if (!sameQty(stdDailyQty, qty)) {
                n++;
            }
        } else if (serverAdjusted.contains("daily_qty")) {
            n++;
        }
        return n;
    }

    private static boolean sameQty(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    // ===== 文字 =====

    /** “61–100 日龄”“141 日龄至出栏” */
    @NonNull
    public String rangeText() {
        if (dayAgeEnd == null) {
            return dayAgeStart + " 日龄至出栏";
        }
        return dayAgeStart + "–" + dayAgeEnd + " 日龄";
    }

    /** 收起时的摘要“育肥全价料 · 每头 2.8 千克 · 3 餐” */
    @NonNull
    public String summaryLine() {
        BigDecimal qty = dailyQty();
        return FeedFormat.join(feedName, qty == null ? null : "每头 " + FeedFormat.kg(qty), mealsPerDay + " 餐");
    }

    /** 未开始阶段的“10月24日进入，次日起自动换用” */
    @Nullable
    public String enterLine() {
        if (!FeedPlanStageVO.STATE_UPCOMING.equals(state) || enterDate == null) {
            return null;
        }
        return FeedFormat.date(enterDate) + "进入，次日起自动换用";
    }

    /** 汇总行右侧：当前阶段“480 千克 · 每餐 160 千克”；未开始阶段为 null（左侧整句估算） */
    @Nullable
    public String todayPlanText(@Nullable Integer currentCount) {
        BigDecimal qty = dailyQty();
        if (!isCurrent() || qty == null || currentCount == null) {
            return null;
        }
        BigDecimal day = qty.multiply(BigDecimal.valueOf(currentCount)).setScale(2, RoundingMode.HALF_UP);
        String s = FeedFormat.kg(day);
        if (mealsPerDay > 0) {
            BigDecimal per = day.divide(BigDecimal.valueOf(mealsPerDay), 2, RoundingMode.HALF_UP);
            s = s + FeedFormat.DOT + "每餐 " + FeedFormat.kg(per);
        }
        return s;
    }

    /** 汇总行左侧：当前阶段“今天计划量”；未开始阶段“按当前存栏 200 头估算，每天 560 千克” */
    @NonNull
    public String summaryLabel(@Nullable Integer currentCount) {
        if (isCurrent()) {
            return "今天计划量";
        }
        BigDecimal qty = dailyQty();
        if (qty == null || currentCount == null) {
            return "按当前存栏估算";
        }
        BigDecimal day = qty.multiply(BigDecimal.valueOf(currentCount)).setScale(2, RoundingMode.HALF_UP);
        return "按当前存栏 " + FeedFormat.head(currentCount) + "估算，每天 " + FeedFormat.kg(day);
    }

    @NonNull
    private static String plain(@NonNull BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
    }
}
