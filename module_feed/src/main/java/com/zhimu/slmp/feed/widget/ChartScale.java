package com.zhimu.slmp.feed.widget;

/**
 * 折线图 / 柱状图的坐标换算（纯计算，便于单元测试）：
 * 横坐标 = 左 +（值 − 起点）÷（终点 − 起点）× 宽；纵坐标 = 底 −（值 − 最小值）÷（最大值 − 最小值）× 高。
 * 轴范围按步长向外取整（G06 横轴取整到 10、纵轴取整到 20；G04 纵轴按“好看”的步长 1 / 2 / 5 × 10ⁿ）。
 */
public final class ChartScale {

    public final float start;
    public final float end;
    public final float pixelStart;
    public final float pixelLength;
    /** true 时像素从下往上增长（纵轴） */
    public final boolean inverted;

    private ChartScale(float start, float end, float pixelStart, float pixelLength, boolean inverted) {
        this.start = start;
        this.end = end;
        this.pixelStart = pixelStart;
        this.pixelLength = pixelLength;
        this.inverted = inverted;
    }

    /** 横轴：左边界 left、宽度 width */
    public static ChartScale horizontal(float start, float end, float left, float width) {
        return new ChartScale(start, end, left, width, false);
    }

    /** 纵轴：底边界 bottom、高度 height（数值越大越靠上） */
    public static ChartScale vertical(float min, float max, float bottom, float height) {
        return new ChartScale(min, max, bottom, height, true);
    }

    /** 数值 → 像素 */
    public float toPixel(float value) {
        float span = end - start;
        float ratio = span == 0f ? 0f : (value - start) / span;
        return inverted ? pixelStart - ratio * pixelLength : pixelStart + ratio * pixelLength;
    }

    /** 像素 → 数值 */
    public float toValue(float pixel) {
        if (pixelLength == 0f) {
            return start;
        }
        float ratio = inverted ? (pixelStart - pixel) / pixelLength : (pixel - pixelStart) / pixelLength;
        return start + ratio * (end - start);
    }

    /** 向下取整到 step 的倍数（负数同样向下） */
    public static int floorTo(float value, int step) {
        return (int) Math.floor(value / step) * step;
    }

    /** 向上取整到 step 的倍数 */
    public static int ceilTo(float value, int step) {
        return (int) Math.ceil(value / step) * step;
    }

    /**
     * 轴范围向外取整：[floorTo(min), ceilTo(max)]；两端相等时终点再加一个步长，保证跨度大于 0。
     *
     * @return {起点, 终点}
     */
    public static int[] niceRange(float min, float max, int step) {
        int lo = floorTo(Math.min(min, max), step);
        int hi = ceilTo(Math.max(min, max), step);
        if (hi <= lo) {
            hi = lo + step;
        }
        return new int[]{lo, hi};
    }

    /**
     * 0 起的纵轴“好看”步长：把 max 分成约 targetTicks 段，步长取 1 / 2 / 5 × 10ⁿ（480 千克 → 步长 200，刻度 0–600）。
     */
    public static int niceStep(float max, int targetTicks) {
        if (max <= 0f || targetTicks <= 0) {
            return 1;
        }
        double raw = max / targetTicks;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double residual = raw / magnitude;
        double nice;
        if (residual <= 1) {
            nice = 1;
        } else if (residual <= 2) {
            nice = 2;
        } else if (residual <= 5) {
            nice = 5;
        } else {
            nice = 10;
        }
        return (int) Math.max(1, Math.round(nice * magnitude));
    }

    /** 横轴最多 maxLabels 个标签时的间隔（7 天每天标，30 天每 5 天标） */
    public static int labelInterval(int count, int maxLabels) {
        if (count <= maxLabels || maxLabels <= 0) {
            return 1;
        }
        return (int) Math.ceil(count / (double) maxLabels);
    }

    /** 根据实际宽度保留不重叠的日龄标签；包含两端，内部刻度使用整十的自然步长。 */
    public static int[] dayAgeLabels(int start, int end, float width, float minLabelGap) {
        if (end <= start) return new int[]{start};
        int span = end - start;
        int intervals = Math.max(1, (int) (width / Math.max(1f, minLabelGap)));
        int step = Math.max(10, niceStep(span, intervals));
        int minDays = Math.max(1, (int) Math.ceil(span * minLabelGap / Math.max(1f, width)));
        java.util.List<Integer> labels = new java.util.ArrayList<>();
        labels.add(start);
        for (int value = ceilTo(start + 1, step); value < end; value += step) {
            if (value - labels.get(labels.size() - 1) >= minDays && end - value >= minDays) {
                labels.add(value);
            }
        }
        labels.add(end);
        int[] result = new int[labels.size()];
        for (int i = 0; i < result.length; i++) result[i] = labels.get(i);
        return result;
    }
}
