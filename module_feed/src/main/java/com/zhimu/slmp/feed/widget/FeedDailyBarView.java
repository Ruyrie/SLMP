package com.zhimu.slmp.feed.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * G04 每日投喂量柱状图（技术文档 2.7.2）：柱宽 24 dp、顶部圆角 4 dp、primary 填充；计划量 FLAT 画水平虚线，
 * STEP 画阶梯虚线；偏差超过阈值的柱在不超过 7 天时柱顶上方 4 dp 标数值。超过 7 天时柱宽自适应（最小 4 dp，
 * 柱间距为柱宽一半）、横轴最多 7 个日期、不标数值。ACTION_UP 命中柱所在区间回调 onBarClick，其他柱降到 60%；
 * 设置新数据时柱高 300 ms 从底部长出。
 */
public class FeedDailyBarView extends View {

    public interface OnBarClickListener {
        void onBarClick(int index, float centerX, float topY);

        void onBlankClick();
    }

    private static final long GROW_MS = 300;

    private final float density;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF plot = new RectF();
    private final RectF barRect = new RectF();
    private final Path barPath = new Path();
    private final Path planPath = new Path();
    private final float[] radii = new float[8];

    @Nullable
    private BarChartModel model;
    @Nullable
    private OnBarClickListener listener;
    private int selected = -1;
    private float growth = 1f;
    @Nullable
    private ValueAnimator animator;

    private float[] centers = new float[0];
    private float[] tops = new float[0];
    private float barWidth;
    private float slotWidth;
    private int labelInterval = 1;

    private final float axisLeft;
    private final float axisBottom;
    private final float padTop;
    private final float maxBarWidth;
    private final float minBarWidth;

    public FeedDailyBarView(@NonNull Context context) {
        this(context, null);
    }

    public FeedDailyBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        axisLeft = dp(32);
        axisBottom = dp(24);
        padTop = dp(20);
        maxBarWidth = dp(24);
        minBarWidth = dp(4);
        float r = dp(4);
        radii[0] = r;
        radii[1] = r;
        radii[2] = r;
        radii[3] = r;

        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        int outlineVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        int onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics());

        gridPaint.setColor(outlineVariant);
        gridPaint.setStrokeWidth(dp(1));
        axisPaint.setColor(onVariant);
        axisPaint.setTextSize(textSize);
        valuePaint.setColor(onVariant);
        valuePaint.setTextSize(textSize);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        barPaint.setColor(primary);
        barPaint.setStyle(Paint.Style.FILL);
        planPaint.setColor(outline);
        planPaint.setStyle(Paint.Style.STROKE);
        planPaint.setStrokeWidth(dp(2));
        planPaint.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(3)}, 0));
        setClickable(true);
    }

    public void setOnBarClickListener(@Nullable OnBarClickListener l) {
        listener = l;
    }

    /** 新数据：柱子从底部长出 */
    public void setData(@Nullable BarChartModel data) {
        model = data;
        selected = -1;
        if (animator != null) {
            animator.cancel();
        }
        rebuild();
        if (data != null && !data.bars.isEmpty() && isAttachedToWindow()) {
            growth = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(GROW_MS);
            animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            animator.addUpdateListener(a -> {
                growth = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            growth = 1f;
        }
        invalidate();
    }

    /** 选中某根柱（-1 取消），其他柱透明度降到 60% */
    public void setSelected(int index) {
        if (selected != index) {
            selected = index;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, resolveSize((int) dp(180), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private void rebuild() {
        plot.set(getPaddingLeft() + axisLeft, getPaddingTop() + padTop, getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom() - axisBottom);
        BarChartModel m = model;
        planPath.reset();
        if (m == null || m.bars.isEmpty() || plot.width() <= 0) {
            centers = new float[0];
            tops = new float[0];
            return;
        }
        int n = m.bars.size();
        slotWidth = plot.width() / n;
        // 柱间距为柱宽一半：slot = 1.5 × 柱宽
        barWidth = Math.max(minBarWidth, Math.min(maxBarWidth, slotWidth / 1.5f));
        labelInterval = ChartScale.labelInterval(n, BarChartModel.MAX_X_LABELS);
        ChartScale ys = ChartScale.vertical(0, m.yMax, plot.bottom, plot.height());
        centers = new float[n];
        tops = new float[n];
        for (int i = 0; i < n; i++) {
            centers[i] = plot.left + slotWidth * (i + 0.5f);
            tops[i] = ys.toPixel(m.bars.get(i).actual);
        }
        if (m.stepLine) {
            for (int i = 0; i < n; i++) {
                float y = ys.toPixel(m.bars.get(i).plan);
                float x0 = plot.left + slotWidth * i;
                float x1 = x0 + slotWidth;
                if (i == 0) {
                    planPath.moveTo(x0, y);
                } else {
                    planPath.lineTo(x0, y);
                }
                planPath.lineTo(x1, y);
            }
        } else if (m.planValue > 0) {
            float y = ys.toPixel(m.planValue);
            planPath.moveTo(plot.left, y);
            planPath.lineTo(plot.right, y);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        BarChartModel m = model;
        if (m == null || plot.width() <= 0) {
            return;
        }
        ChartScale ys = ChartScale.vertical(0, m.yMax, plot.bottom, plot.height());
        float textOffset = (axisPaint.descent() + axisPaint.ascent()) / 2f;
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        for (int v = 0; v <= m.yMax; v += m.yStep) {
            float y = ys.toPixel(v);
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
            canvas.drawText(String.valueOf(v), plot.left - dp(6), y - textOffset, axisPaint);
        }
        axisPaint.setTextAlign(Paint.Align.CENTER);
        float labelY = plot.bottom + dp(8) - axisPaint.ascent();
        boolean values = m.showValueLabels();
        for (int i = 0; i < centers.length; i++) {
            BarChartModel.Bar bar = m.bars.get(i);
            float top = plot.bottom - (plot.bottom - tops[i]) * growth;
            barPaint.setAlpha(selected < 0 || selected == i ? 255 : 153);
            if (top < plot.bottom) {
                barRect.set(centers[i] - barWidth / 2f, top, centers[i] + barWidth / 2f, plot.bottom);
                barPath.reset();
                barPath.addRoundRect(barRect, radii, Path.Direction.CW);
                canvas.drawPath(barPath, barPaint);
            }
            if (values && bar.overThreshold && bar.valueLabel != null && growth >= 1f) {
                canvas.drawText(bar.valueLabel, centers[i], top - dp(4) - valuePaint.descent(), valuePaint);
            }
            if (i % labelInterval == 0) {
                canvas.drawText(bar.label, centers[i], labelY, axisPaint);
            }
        }
        barPaint.setAlpha(255);
        canvas.drawPath(planPath, planPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_UP:
                int index = indexAt(event.getX(), event.getY());
                if (index >= 0) {
                    performClick();
                    if (listener != null) {
                        listener.onBarClick(index, centers[index], Math.min(tops[index], plot.bottom));
                    }
                } else if (listener != null) {
                    listener.onBlankClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /** 命中的柱（所在区间内、绘图区纵向范围内） */
    int indexAt(float x, float y) {
        if (centers.length == 0 || x < plot.left || x > plot.right || y < getPaddingTop() || y > plot.bottom + dp(8)) {
            return -1;
        }
        int i = (int) ((x - plot.left) / slotWidth);
        return i >= 0 && i < centers.length ? i : -1;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        growth = 1f;
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * density;
    }
}
