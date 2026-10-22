package com.zhimu.slmp.feed.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.feed.model.vo.FeedExecSummaryVO;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FeedDailyBarView 的输入（技术文档 2.7.2）：每天一根柱（实际投喂量），计划线为水平虚线（FLAT）或阶梯虚线（STEP）；
 * 偏差超过阈值的柱在不超过 7 天时标数值；纵轴 0 起，步长取 1 / 2 / 5 × 10ⁿ（计划 480 千克时刻度 0 / 200 / 400 / 600）。
 */
public final class BarChartModel {

    /** 一根柱 */
    public static final class Bar {
        @NonNull
        public final String date;
        /** 横轴标签“12日” */
        @NonNull
        public final String label;
        public final float actual;
        public final float plan;
        public final boolean overThreshold;
        /** 柱顶数值“312”（只在偏差超过阈值时） */
        @Nullable
        public final String valueLabel;
        /** 点柱提示“16日 · 实投 312 千克 · 计划 480 千克 · 完成 2 / 3 餐” */
        @NonNull
        public final String tip;

        public Bar(@NonNull String date, @NonNull String label, float actual, float plan, boolean overThreshold,
                   @Nullable String valueLabel, @NonNull String tip) {
            this.date = date;
            this.label = label;
            this.actual = actual;
            this.plan = plan;
            this.overThreshold = overThreshold;
            this.valueLabel = valueLabel;
            this.tip = tip;
        }
    }

    public static final int MAX_VALUE_LABEL_DAYS = 7;
    public static final int MAX_X_LABELS = 7;

    @NonNull
    public final List<Bar> bars;
    public final boolean stepLine;
    /** FLAT 时的计划值 */
    public final float planValue;
    public final int yMax;
    public final int yStep;

    public BarChartModel(@NonNull List<Bar> bars, boolean stepLine, float planValue, int yMax, int yStep) {
        this.bars = Collections.unmodifiableList(bars);
        this.stepLine = stepLine;
        this.planValue = planValue;
        this.yMax = yMax;
        this.yStep = yStep;
    }

    /** 超过 7 天时柱顶不标数值 */
    public boolean showValueLabels() {
        return bars.size() <= MAX_VALUE_LABEL_DAYS;
    }

    @NonNull
    public static BarChartModel from(@NonNull FeedExecSummaryVO vo) {
        List<Bar> bars = new ArrayList<>();
        float max = 0f;
        boolean step = FeedExecSummaryVO.LINE_STEP.equals(vo.getPlanLineType());
        float planValue = vo.getPlanDailyQty() == null ? 0f : vo.getPlanDailyQty().floatValue();
        for (FeedExecSummaryVO.FeedExecDailyVO d : vo.getDaily()) {
            float actual = d.getActualQty() == null ? 0f : d.getActualQty().floatValue();
            float plan = d.getPlanQty() == null ? planValue : d.getPlanQty().floatValue();
            max = Math.max(max, Math.max(actual, plan));
            String day = FeedFormat.dayOfMonth(d.getDate());
            String tip = day + FeedFormat.DOT + "实投 " + FeedFormat.kg(d.getActualQty()) + FeedFormat.DOT + "计划 "
                    + FeedFormat.kg(d.getPlanQty()) + FeedFormat.DOT + "完成 " + d.getDoneMeals() + " / "
                    + d.getPlannedMeals() + " 餐";
            bars.add(new Bar(FeedFormat.orEmpty(d.getDate()), day, actual, plan, d.isOverThreshold(),
                    d.isOverThreshold() ? FeedFormat.num(d.getActualQty()) : null, tip));
        }
        if (!step) {
            max = Math.max(max, planValue);
        }
        int yStep = ChartScale.niceStep(max <= 0 ? 1 : max, 3);
        int yMax = Math.max(yStep, ChartScale.ceilTo(max, yStep));
        if (yMax == ChartScale.ceilTo(max, yStep) && max > 0 && yMax - max < yStep * 0.15f) {
            yMax += yStep;
        }
        return new BarChartModel(bars, step, planValue, yMax, yStep);
    }
}
