package com.zhimu.slmp.feed.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.feed.model.vo.GrowthCurveVO;
import com.zhimu.slmp.feed.model.vo.StdPointVO;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.util.GrowthCalc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GrowthCurveView 的输入（技术文档 2.7.1）：横轴 x_min、x_max 向外取整到 10，纵轴按全部可见值向外取整到 20；
 * 实际点（入栏点空心、最新点放大）、标准点（含偏差带 lower、upper）、对比系列、标注“标准 68.6”“60.0”。
 * 由 ViewModel 在 computation 线程从 GrowthCurveVO 组装（{@link #from}），只含基本类型与字符串。
 */
public final class GrowthChartModel {

    public static final int X_STEP = 10;
    public static final int Y_STEP = 20;

    /** 一个数据点 */
    public static final class Point {
        public final int dayAge;
        public final float weight;
        public final boolean entry;
        /** 点按提示：“10月5日 · 82 日龄 · 48.6 千克（标准 53.2）” */
        @NonNull
        public final String tip;

        public Point(int dayAge, float weight, boolean entry, @NonNull String tip) {
            this.dayAge = dayAge;
            this.weight = weight;
            this.entry = entry;
            this.tip = tip;
        }
    }

    /** 标准点与偏差带 */
    public static final class StdPoint {
        public final int dayAge;
        public final float std;
        public final float lower;
        public final float upper;

        public StdPoint(int dayAge, float std, float lower, float upper) {
            this.dayAge = dayAge;
            this.std = std;
            this.lower = lower;
            this.upper = upper;
        }
    }

    /** 对比系列 */
    public static final class Series {
        public final long batchId;
        @NonNull
        public final String batchNo;
        @NonNull
        public final List<Point> points;

        public Series(long batchId, @NonNull String batchNo, @NonNull List<Point> points) {
            this.batchId = batchId;
            this.batchNo = batchNo;
            this.points = points;
        }
    }

    public final int xStart;
    public final int xEnd;
    public final int yMin;
    public final int yMax;
    @NonNull
    public final List<Point> actual;
    @NonNull
    public final List<StdPoint> std;
    @NonNull
    public final List<Series> compare;
    /** 最近一次称重在 actual 中的下标；没有称重时为 -1 */
    public final int latestIndex;
    public final boolean stdAvailable;
    /** “标准 68.6”，标在当前日龄处的标准线上方；没有标准时为 null */
    @Nullable
    public final String stdLabel;
    public final float stdLabelDay;
    public final float stdLabelWeight;
    /** 最新点下方的“60.0” */
    @Nullable
    public final String latestLabel;

    public GrowthChartModel(int xStart, int xEnd, int yMin, int yMax, @NonNull List<Point> actual,
                            @NonNull List<StdPoint> std, @NonNull List<Series> compare, int latestIndex,
                            boolean stdAvailable, @Nullable String stdLabel, float stdLabelDay, float stdLabelWeight,
                            @Nullable String latestLabel) {
        this.xStart = xStart;
        this.xEnd = xEnd;
        this.yMin = yMin;
        this.yMax = yMax;
        this.actual = Collections.unmodifiableList(actual);
        this.std = Collections.unmodifiableList(std);
        this.compare = Collections.unmodifiableList(compare);
        this.latestIndex = latestIndex;
        this.stdAvailable = stdAvailable;
        this.stdLabel = stdLabel;
        this.stdLabelDay = stdLabelDay;
        this.stdLabelWeight = stdLabelWeight;
        this.latestLabel = latestLabel;
    }

    /** 组装图表模型；没有实际点也没有标准点时返回 null */
    @Nullable
    public static GrowthChartModel from(@NonNull GrowthCurveVO vo) {
        List<Point> actual = new ArrayList<>();
        int latest = -1;
        for (GrowthCurveVO.GrowthActualPointVO p : vo.getActualPoints()) {
            if (p.getAvgWeight() == null) {
                continue;
            }
            actual.add(new Point(p.getDayAge(), p.getAvgWeight().floatValue(), p.isEntry(), actualTip(p)));
            if (!p.isEntry()) {
                latest = actual.size() - 1;
            }
        }
        List<StdPoint> std = new ArrayList<>();
        boolean stdAvailable = vo.isStdAvailable();
        if (stdAvailable) {
            for (StdPointVO s : vo.getStdPoints()) {
                if (s.getStdWeight() == null) {
                    continue;
                }
                float w = s.getStdWeight().floatValue();
                std.add(new StdPoint(s.getDayAge(), w, s.getLower() == null ? w : s.getLower().floatValue(),
                        s.getUpper() == null ? w : s.getUpper().floatValue()));
            }
            stdAvailable = !std.isEmpty();
        }
        List<Series> compare = new ArrayList<>();
        for (GrowthCurveVO.GrowthCompareSeriesVO s : vo.getCompareSeries()) {
            List<Point> pts = new ArrayList<>();
            String no = FeedFormat.orEmpty(s.getBatchNo());
            for (GrowthCurveVO.GrowthComparePointVO p : s.getPoints()) {
                if (p.getAvgWeight() != null) {
                    pts.add(new Point(p.getDayAge(), p.getAvgWeight().floatValue(), false,
                            no + FeedFormat.DOT + p.getDayAge() + " 日龄" + FeedFormat.DOT + FeedFormat.kg1(p.getAvgWeight())));
                }
            }
            compare.add(new Series(s.getBatchId(), no, pts));
        }
        if (actual.isEmpty() && std.isEmpty() && compare.isEmpty()) {
            return null;
        }

        // 横轴：x_min、x_max 向外取整到 10；没有给出时取数据范围
        float xLo = vo.getXMin() != null ? vo.getXMin() : Float.MAX_VALUE;
        float xHi = vo.getXMax() != null ? vo.getXMax() : -Float.MAX_VALUE;
        if (vo.getXMin() == null || vo.getXMax() == null) {
            for (Point p : actual) {
                xLo = Math.min(xLo, p.dayAge);
                xHi = Math.max(xHi, p.dayAge);
            }
            for (StdPoint p : std) {
                xLo = Math.min(xLo, p.dayAge);
                xHi = Math.max(xHi, p.dayAge);
            }
        }
        int[] x = ChartScale.niceRange(xLo, xHi, X_STEP);

        // 纵轴：横轴范围内全部可见值（实际、标准与偏差带、对比）向外取整到 20
        float yLo = Float.MAX_VALUE;
        float yHi = -Float.MAX_VALUE;
        for (Point p : actual) {
            yLo = Math.min(yLo, p.weight);
            yHi = Math.max(yHi, p.weight);
        }
        for (StdPoint p : std) {
            if (p.dayAge >= x[0] && p.dayAge <= x[1]) {
                yLo = Math.min(yLo, p.lower);
                yHi = Math.max(yHi, p.upper);
            }
        }
        // 偏差带被横轴裁剪的两端按插值补上，避免纵轴偏小
        Float edgeLo = bandAt(std, x[0]);
        Float edgeHi = bandAt(std, x[1]);
        if (edgeLo != null) {
            yLo = Math.min(yLo, edgeLo);
        }
        if (edgeHi != null) {
            yHi = Math.max(yHi, upperAt(std, x[1]));
        }
        for (Series s : compare) {
            for (Point p : s.points) {
                if (p.dayAge >= x[0] && p.dayAge <= x[1]) {
                    yLo = Math.min(yLo, p.weight);
                    yHi = Math.max(yHi, p.weight);
                }
            }
        }
        if (yLo == Float.MAX_VALUE) {
            yLo = 0f;
            yHi = Y_STEP;
        }
        int[] y = ChartScale.niceRange(yLo, yHi, Y_STEP);

        String stdLabel = null;
        float stdDay = 0f;
        float stdWeight = 0f;
        Integer current = vo.getCurrentDayAge();
        if (stdAvailable && current != null) {
            List<StdPointVO> pts = vo.getStdPoints();
            BigDecimal at = GrowthCalc.interpolate(pts, current);
            if (at != null) {
                stdLabel = "标准 " + FeedFormat.num1(at);
                stdDay = current;
                stdWeight = at.floatValue();
            }
        }
        String latestLabel = latest >= 0 ? FeedFormat.num1(BigDecimal.valueOf(actual.get(latest).weight)) : null;
        return new GrowthChartModel(x[0], x[1], y[0], y[1], actual, std, compare, latest, stdAvailable,
                stdLabel, stdDay, stdWeight, latestLabel);
    }

    /** 去掉某个对比系列（本地移除，不重新请求） */
    @NonNull
    public GrowthChartModel withoutSeries(long batchId) {
        List<Series> left = new ArrayList<>();
        for (Series s : compare) {
            if (s.batchId != batchId) {
                left.add(s);
            }
        }
        return new GrowthChartModel(xStart, xEnd, yMin, yMax, actual, std, left, latestIndex, stdAvailable, stdLabel,
                stdLabelDay, stdLabelWeight, latestLabel);
    }

    @NonNull
    private static String actualTip(@NonNull GrowthCurveVO.GrowthActualPointVO p) {
        StringBuilder sb = new StringBuilder();
        sb.append(FeedFormat.date(p.getDate())).append(FeedFormat.DOT).append(p.getDayAge()).append(" 日龄")
                .append(FeedFormat.DOT).append(FeedFormat.kg1(p.getAvgWeight()));
        if (p.isEntry()) {
            sb.append("（入栏）");
        } else if (p.getStdWeight() != null) {
            sb.append("（标准 ").append(FeedFormat.num1(p.getStdWeight())).append("）");
        }
        return sb.toString();
    }

    /** 偏差带下沿在某日龄处的插值；超出标准点范围时为 null */
    @Nullable
    static Float bandAt(@NonNull List<StdPoint> std, float day) {
        for (int i = 1; i < std.size(); i++) {
            StdPoint a = std.get(i - 1);
            StdPoint b = std.get(i);
            if (day >= a.dayAge && day <= b.dayAge && b.dayAge > a.dayAge) {
                float t = (day - a.dayAge) / (b.dayAge - a.dayAge);
                return a.lower + (b.lower - a.lower) * t;
            }
        }
        return null;
    }

    static float upperAt(@NonNull List<StdPoint> std, float day) {
        for (int i = 1; i < std.size(); i++) {
            StdPoint a = std.get(i - 1);
            StdPoint b = std.get(i);
            if (day >= a.dayAge && day <= b.dayAge && b.dayAge > a.dayAge) {
                float t = (day - a.dayAge) / (b.dayAge - a.dayAge);
                return a.upper + (b.upper - a.upper) * t;
            }
        }
        return std.isEmpty() ? 0f : std.get(std.size() - 1).upper;
    }
}
