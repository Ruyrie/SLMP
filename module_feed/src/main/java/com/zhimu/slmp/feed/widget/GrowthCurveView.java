package com.zhimu.slmp.feed.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * G06 生长曲线：宽随父容器，高固定 200 dp；横轴日龄标签按可用宽度稀疏排列，纵轴平均体重（千克）。
 * <p>
 * 图层：网格与刻度 → 允许偏差带（surface-container-high）→ 标准线（2 dp outline 虚线）→ 对比线（2 dp outline 点线）
 * → 实际线（2.5 dp primary）→ 数据点（8 dp，2 dp 白色描边；入栏点空心；最新点 10 dp）→ 标注“标准 68.6”“60.0”。
 * 坐标见 {@link ChartScale}；setData、onSizeChanged 时重建 Path 与点坐标，onDraw 不创建对象；绘图区 clipRect，不外推。
 * <p>
 * 触摸：ACTION_DOWN 返回 true；ACTION_UP 时在 24 dp 半径内找最近的点，命中回调 {@link OnPointClickListener#onPointClick}
 * 并 performClick，点空白回调 onBlankClick（关闭气泡）；不拦截 MOVE（父容器可滚动）。
 * 新增对比线 350 ms 从左到右描出（PathMeasure.getSegment），移除时 200 ms 淡出；没有标准不画偏差带与标准线；
 * 只有一个实际点时只画圆点；onDetachedFromWindow 取消动画。
 */
public class GrowthCurveView extends View {

    /** 点按回调：series 为 0 表示实际线，1 起为对比系列（model.compare 下标 + 1） */
    public interface OnPointClickListener {
        void onPointClick(int series, int index, float x, float y);

        void onBlankClick();
    }

    private static final long DRAW_IN_MS = 350;
    private static final long FADE_OUT_MS = 200;

    private final float density;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint comparePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint comparePointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actualPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF plot = new RectF();
    private final Path bandPath = new Path();
    private final Path stdPath = new Path();
    private final Path actualPath = new Path();
    private float[] actualX = new float[0];
    private float[] actualY = new float[0];
    private final List<SeriesPath> series = new ArrayList<>();
    private final List<SeriesPath> fading = new ArrayList<>();
    private final Map<Long, ValueAnimator> animators = new HashMap<>();

    private String[] yLabels = new String[0];
    private float[] yLabelPos = new float[0];
    private String[] xLabels = new String[0];
    private float[] xLabelPos = new float[0];
    private float stdLabelX;
    private float stdLabelY;
    private float latestX;
    private float latestY;

    @Nullable
    private GrowthChartModel model;
    @Nullable
    private OnPointClickListener listener;

    private final float axisLeft;
    private final float axisBottom;
    private final float padTop;
    private final float padRight;
    private final float hitRadius;
    private final float pointRadius;
    private final float latestRadius;
    private final float pointStrokeWidth;
    private final String unitLabel = "日龄";

    /** 一条对比线的预计算路径与动画状态 */
    private static final class SeriesPath {
        final long id;
        GrowthChartModel.Series data;
        final Path path = new Path();
        final Path segment = new Path();
        final PathMeasure measure = new PathMeasure();
        float length;
        float progress = 1f;
        int alpha = 255;
        float[] xs = new float[0];
        float[] ys = new float[0];

        SeriesPath(@NonNull GrowthChartModel.Series data) {
            this.id = data.batchId;
            this.data = data;
        }
    }

    public GrowthCurveView(@NonNull Context context) {
        this(context, null);
    }

    public GrowthCurveView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        axisLeft = dp(30);
        axisBottom = dp(22);
        padTop = dp(12);
        padRight = dp(30);
        hitRadius = dp(24);
        pointRadius = dp(4);
        latestRadius = dp(5);
        pointStrokeWidth = dp(2);

        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        int outlineVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        int band = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int white = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLowest);

        gridPaint.setColor(outlineVariant);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setStyle(Paint.Style.STROKE);

        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics());
        axisTextPaint.setColor(onVariant);
        axisTextPaint.setTextSize(textSize);
        labelPaint.setColor(onVariant);
        labelPaint.setTextSize(textSize);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        latestLabelPaint.setColor(primary);
        latestLabelPaint.setTextSize(textSize);
        latestLabelPaint.setTextAlign(Paint.Align.CENTER);

        bandPaint.setColor(band);
        bandPaint.setStyle(Paint.Style.FILL);

        stdPaint.setColor(outline);
        stdPaint.setStyle(Paint.Style.STROKE);
        stdPaint.setStrokeWidth(dp(2));
        stdPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));

        comparePaint.setColor(outline);
        comparePaint.setStyle(Paint.Style.STROKE);
        comparePaint.setStrokeWidth(dp(2));
        comparePaint.setStrokeCap(Paint.Cap.ROUND);
        comparePaint.setPathEffect(new DashPathEffect(new float[]{dp(0.5f), dp(4)}, 0));
        comparePointPaint.setColor(outline);
        comparePointPaint.setStyle(Paint.Style.STROKE);
        comparePointPaint.setStrokeWidth(dp(2));

        actualPaint.setColor(primary);
        actualPaint.setStyle(Paint.Style.STROKE);
        actualPaint.setStrokeWidth(dp(2.5f));
        actualPaint.setStrokeJoin(Paint.Join.ROUND);
        actualPaint.setStrokeCap(Paint.Cap.ROUND);

        pointFill.setColor(primary);
        pointFill.setStyle(Paint.Style.FILL);
        pointStroke.setColor(white);
        pointStroke.setStyle(Paint.Style.STROKE);
        pointStroke.setStrokeWidth(pointStrokeWidth);
        setClickable(true);
    }

    public void setOnPointClickListener(@Nullable OnPointClickListener l) {
        listener = l;
    }

    @Nullable
    public GrowthChartModel getModel() {
        return model;
    }

    /** 设置数据：新增的对比线逐段描出，移除的对比线淡出 */
    public void setData(@Nullable GrowthChartModel next) {
        Set<Long> nextIds = new HashSet<>();
        if (next != null) {
            for (GrowthChartModel.Series s : next.compare) {
                nextIds.add(s.batchId);
            }
        }
        Set<Long> oldIds = new HashSet<>();
        for (Iterator<SeriesPath> it = series.iterator(); it.hasNext(); ) {
            SeriesPath sp = it.next();
            oldIds.add(sp.id);
            if (!nextIds.contains(sp.id)) {
                it.remove();
                startFade(sp);
            }
        }
        boolean firstData = model == null;
        model = next;
        if (next != null) {
            List<SeriesPath> updated = new ArrayList<>();
            for (GrowthChartModel.Series s : next.compare) {
                SeriesPath sp = find(s.batchId);
                if (sp == null) {
                    sp = new SeriesPath(s);
                    if (!firstData && !oldIds.contains(s.batchId)) {
                        sp.progress = 0f;
                        startDrawIn(sp);
                    }
                } else {
                    sp.data = s;
                }
                updated.add(sp);
            }
            series.clear();
            series.addAll(updated);
        } else {
            series.clear();
        }
        rebuild();
        invalidate();
    }

    @Nullable
    private SeriesPath find(long id) {
        for (SeriesPath sp : series) {
            if (sp.id == id) {
                return sp;
            }
        }
        return null;
    }

    private void startDrawIn(@NonNull SeriesPath sp) {
        cancelAnimator(sp.id);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(DRAW_IN_MS);
        a.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        a.addUpdateListener(v -> {
            sp.progress = (float) v.getAnimatedValue();
            invalidate();
        });
        animators.put(sp.id, a);
        a.start();
    }

    private void startFade(@NonNull SeriesPath sp) {
        cancelAnimator(sp.id);
        fading.add(sp);
        ValueAnimator a = ValueAnimator.ofInt(sp.alpha, 0);
        a.setDuration(FADE_OUT_MS);
        a.addUpdateListener(v -> {
            sp.alpha = (int) v.getAnimatedValue();
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fading.remove(sp);
                invalidate();
            }
        });
        animators.put(-sp.id - 1, a);
        a.start();
    }

    private void cancelAnimator(long key) {
        ValueAnimator old = animators.remove(key);
        if (old != null) {
            old.cancel();
        }
    }

    // ===================== 测量与预计算 =====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) dp(200);
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    /** 当前尺寸下的横轴换算（绘图区左右边界） */
    @NonNull
    public ChartScale xScale() {
        GrowthChartModel m = model;
        return ChartScale.horizontal(m == null ? 0 : m.xStart, m == null ? 1 : m.xEnd, plot.left, plot.width());
    }

    @NonNull
    public ChartScale yScale() {
        GrowthChartModel m = model;
        return ChartScale.vertical(m == null ? 0 : m.yMin, m == null ? 1 : m.yMax, plot.bottom, plot.height());
    }

    private void rebuild() {
        GrowthChartModel m = model;
        float rightSpace = m == null ? padRight : Math.max(padRight,
                axisTextPaint.measureText(unitLabel) + axisTextPaint.measureText(String.valueOf(m.xEnd)) / 2f + dp(8));
        plot.set(getPaddingLeft() + axisLeft, getPaddingTop() + padTop, getWidth() - getPaddingRight() - rightSpace,
                getHeight() - getPaddingBottom() - axisBottom);
        bandPath.reset();
        stdPath.reset();
        actualPath.reset();
        if (m == null || plot.width() <= 0 || plot.height() <= 0) {
            actualX = new float[0];
            actualY = new float[0];
            yLabels = new String[0];
            xLabels = new String[0];
            return;
        }
        ChartScale xs = xScale();
        ChartScale ys = yScale();

        int yCount = Math.round((m.yMax - m.yMin) / m.yStep) + 1;
        yLabels = new String[yCount];
        yLabelPos = new float[yCount];
        for (int i = 0; i < yCount; i++) {
            float v = m.yMin + i * m.yStep;
            yLabels[i] = java.math.BigDecimal.valueOf(v).setScale(3, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            yLabelPos[i] = ys.toPixel(v);
        }
        float labelWidth = Math.max(axisTextPaint.measureText(String.valueOf(m.xStart)),
                axisTextPaint.measureText(String.valueOf(m.xEnd)));
        int[] ages = ChartScale.dayAgeLabels(m.xStart, m.xEnd, plot.width(), labelWidth + dp(12));
        int xCount = ages.length;
        xLabels = new String[xCount];
        xLabelPos = new float[xCount];
        for (int i = 0; i < xCount; i++) {
            int v = ages[i];
            xLabels[i] = String.valueOf(v);
            xLabelPos[i] = xs.toPixel(v);
        }

        if (m.stdAvailable && m.std.size() >= 2) {
            for (int i = 0; i < m.std.size(); i++) {
                GrowthChartModel.StdPoint p = m.std.get(i);
                float x = xs.toPixel(p.dayAge);
                if (i == 0) {
                    bandPath.moveTo(x, ys.toPixel(p.upper));
                    stdPath.moveTo(x, ys.toPixel(p.std));
                } else {
                    bandPath.lineTo(x, ys.toPixel(p.upper));
                    stdPath.lineTo(x, ys.toPixel(p.std));
                }
            }
            for (int i = m.std.size() - 1; i >= 0; i--) {
                GrowthChartModel.StdPoint p = m.std.get(i);
                bandPath.lineTo(xs.toPixel(p.dayAge), ys.toPixel(p.lower));
            }
            bandPath.close();
        }
        stdLabelX = xs.toPixel(m.stdLabelDay);
        stdLabelY = ys.toPixel(m.stdLabelWeight);

        actualX = new float[m.actual.size()];
        actualY = new float[m.actual.size()];
        for (int i = 0; i < m.actual.size(); i++) {
            GrowthChartModel.Point p = m.actual.get(i);
            actualX[i] = xs.toPixel(p.dayAge);
            actualY[i] = ys.toPixel(p.weight);
            if (i == 0) {
                actualPath.moveTo(actualX[i], actualY[i]);
            } else {
                actualPath.lineTo(actualX[i], actualY[i]);
            }
        }
        if (m.latestIndex >= 0 && m.latestIndex < actualX.length) {
            latestX = actualX[m.latestIndex];
            latestY = actualY[m.latestIndex];
        }
        for (SeriesPath sp : series) {
            buildSeries(sp, xs, ys);
        }
        for (SeriesPath sp : fading) {
            buildSeries(sp, xs, ys);
        }
    }

    private static void buildSeries(@NonNull SeriesPath sp, @NonNull ChartScale xs, @NonNull ChartScale ys) {
        sp.path.reset();
        List<GrowthChartModel.Point> pts = sp.data.points;
        sp.xs = new float[pts.size()];
        sp.ys = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            sp.xs[i] = xs.toPixel(pts.get(i).dayAge);
            sp.ys[i] = ys.toPixel(pts.get(i).weight);
            if (i == 0) {
                sp.path.moveTo(sp.xs[i], sp.ys[i]);
            } else {
                sp.path.lineTo(sp.xs[i], sp.ys[i]);
            }
        }
        sp.measure.setPath(sp.path, false);
        sp.length = sp.measure.getLength();
    }

    // ===================== 绘制 =====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        GrowthChartModel m = model;
        if (m == null || plot.width() <= 0) {
            return;
        }
        // 网格与刻度
        float textOffset = (axisTextPaint.descent() + axisTextPaint.ascent()) / 2f;
        axisTextPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < yLabels.length; i++) {
            float y = yLabelPos[i];
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
            canvas.drawText(yLabels[i], plot.left - dp(6), y - textOffset, axisTextPaint);
        }
        axisTextPaint.setTextAlign(Paint.Align.CENTER);
        float xTextY = plot.bottom + dp(6) - axisTextPaint.ascent();
        for (int i = 0; i < xLabels.length; i++) {
            canvas.drawText(xLabels[i], xLabelPos[i], xTextY, axisTextPaint);
        }
        axisTextPaint.setTextAlign(Paint.Align.LEFT);
        if (xLabelPos.length > 0) {
            float lastX = xLabelPos[xLabelPos.length - 1] + axisTextPaint.measureText(xLabels[xLabels.length - 1]) / 2f
                    + dp(4);
            canvas.drawText(unitLabel, Math.min(lastX, getWidth() - axisTextPaint.measureText(unitLabel)), xTextY,
                    axisTextPaint);
        }

        int save = canvas.save();
        canvas.clipRect(plot);
        if (m.stdAvailable) {
            canvas.drawPath(bandPath, bandPaint);
            canvas.drawPath(stdPath, stdPaint);
        }
        for (SeriesPath sp : series) {
            drawSeries(canvas, sp);
        }
        for (SeriesPath sp : fading) {
            drawSeries(canvas, sp);
        }
        if (actualX.length >= 2) {
            canvas.drawPath(actualPath, actualPaint);
        }
        canvas.restoreToCount(save);

        // 数据点（可略微超出绘图区，不裁剪）
        for (int i = 0; i < actualX.length; i++) {
            boolean latest = i == m.latestIndex;
            float r = latest ? latestRadius : pointRadius;
            if (m.actual.get(i).entry) {
                canvas.drawCircle(actualX[i], actualY[i], r, pointStroke);
                pointFill.setStyle(Paint.Style.STROKE);
                pointFill.setStrokeWidth(pointStrokeWidth);
                canvas.drawCircle(actualX[i], actualY[i], r, pointFill);
                pointFill.setStyle(Paint.Style.FILL);
            } else {
                canvas.drawCircle(actualX[i], actualY[i], r, pointFill);
                canvas.drawCircle(actualX[i], actualY[i], r, pointStroke);
            }
        }
        // Path 只有 moveTo 时不会绘制任何内容；单次称重也必须有可点击、可见的对比点。
        // 空心圈略大于实际点，两个批次数据重合时仍能看出已添加的对比系列。
        for (SeriesPath sp : series) {
            drawComparePoints(canvas, sp);
        }
        for (SeriesPath sp : fading) {
            drawComparePoints(canvas, sp);
        }
        // 标注
        if (m.stdAvailable && m.stdLabel != null) {
            float x = clampLabelX(stdLabelX, labelPaint.measureText(m.stdLabel));
            canvas.drawText(m.stdLabel, x, Math.max(plot.top - labelPaint.ascent(), stdLabelY - dp(8)), labelPaint);
        }
        if (m.latestLabel != null && m.latestIndex >= 0) {
            float x = clampLabelX(latestX, latestLabelPaint.measureText(m.latestLabel));
            float y = Math.min(latestY + latestRadius + dp(4) - latestLabelPaint.ascent(), plot.bottom - dp(2));
            canvas.drawText(m.latestLabel, x, y, latestLabelPaint);
        }
    }

    private void drawSeries(@NonNull Canvas canvas, @NonNull SeriesPath sp) {
        comparePaint.setAlpha(sp.alpha);
        if (sp.progress >= 1f) {
            canvas.drawPath(sp.path, comparePaint);
        } else if (sp.progress > 0f) {
            sp.segment.reset();
            sp.measure.getSegment(0f, sp.length * sp.progress, sp.segment, true);
            canvas.drawPath(sp.segment, comparePaint);
        }
        comparePaint.setAlpha(255);
    }

    private void drawComparePoints(@NonNull Canvas canvas, @NonNull SeriesPath sp) {
        if (sp.progress <= 0f) return;
        comparePointPaint.setAlpha(sp.alpha);
        int visible = Math.min(sp.xs.length, (int) Math.ceil(sp.xs.length * sp.progress));
        for (int i = 0; i < visible; i++) {
            if (sp.xs[i] >= plot.left && sp.xs[i] <= plot.right
                    && sp.ys[i] >= plot.top && sp.ys[i] <= plot.bottom) {
                canvas.drawCircle(sp.xs[i], sp.ys[i], pointRadius + dp(2), comparePointPaint);
            }
        }
        comparePointPaint.setAlpha(255);
    }

    private float clampLabelX(float x, float textWidth) {
        float half = textWidth / 2f;
        return Math.max(plot.left + half, Math.min(x, plot.right - half));
    }

    // ===================== 触摸 =====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_UP:
                handleTap(event.getX(), event.getY());
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleTap(float x, float y) {
        int hitSeries = -1;
        int hitIndex = -1;
        float best = hitRadius * hitRadius;
        for (int i = 0; i < actualX.length; i++) {
            float d = dist2(x, y, actualX[i], actualY[i]);
            if (d <= best) {
                best = d;
                hitSeries = 0;
                hitIndex = i;
            }
        }
        for (int s = 0; s < series.size(); s++) {
            SeriesPath sp = series.get(s);
            for (int i = 0; i < sp.xs.length; i++) {
                float d = dist2(x, y, sp.xs[i], sp.ys[i]);
                if (d < best) {
                    best = d;
                    hitSeries = s + 1;
                    hitIndex = i;
                }
            }
        }
        if (hitSeries >= 0) {
            performClick();
            if (listener != null) {
                float px = hitSeries == 0 ? actualX[hitIndex] : series.get(hitSeries - 1).xs[hitIndex];
                float py = hitSeries == 0 ? actualY[hitIndex] : series.get(hitSeries - 1).ys[hitIndex];
                listener.onPointClick(hitSeries, hitIndex, px, py);
            }
        } else if (listener != null) {
            listener.onBlankClick();
        }
    }

    /** 点按提示文字：series 0 为实际线，1 起为对比系列 */
    @Nullable
    public String tipOf(int seriesIndex, int index) {
        GrowthChartModel m = model;
        if (m == null) {
            return null;
        }
        if (seriesIndex == 0) {
            return index >= 0 && index < m.actual.size() ? m.actual.get(index).tip : null;
        }
        int s = seriesIndex - 1;
        if (s < 0 || s >= m.compare.size()) {
            return null;
        }
        List<GrowthChartModel.Point> pts = m.compare.get(s).points;
        return index >= 0 && index < pts.size() ? pts.get(index).tip : null;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private static float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    @Override
    protected void onDetachedFromWindow() {
        for (ValueAnimator a : animators.values()) {
            a.cancel();
        }
        animators.clear();
        fading.clear();
        for (SeriesPath sp : series) {
            sp.progress = 1f;
            sp.alpha = 255;
        }
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * density;
    }
}
