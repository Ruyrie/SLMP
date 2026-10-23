package com.zhimu.slmp.finance.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 走势图基类（E11 行情折线、E13 报表趋势）：Y 轴 3 个刻度（最高、中间、最低）与 1 dp 网格线，X 轴标起、中、止三个日期；
 * 折线 2.5 dp primary，线下面积 primary 8%；值为空的日期折线断开、不插值；可选平均价虚线、最低价空心点、事件标注、柱状模式；
 * 按住时竖向指示线与数值气泡（requestDisallowInterceptTouchEvent）；setData 后自左向右重绘 350 毫秒。
 */
public class TrendChartView extends View {

    /** 坐标与气泡文字 */
    public interface Formatter {
        @NonNull
        String axis(float value);

        @NonNull
        String date(@NonNull String date);

        @NonNull
        String bubble(@NonNull String date, float value);
    }

    /** 标注在某个点旁边的文字（出栏 202、入栏 200） */
    public static final class Mark {
        final int index;
        final String text;

        public Mark(int index, @NonNull String text) {
            this.index = index;
            this.text = text;
        }
    }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText;
    private final Paint bubbleText;
    private final Paint bubbleBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int primary;
    private final int surface;

    private final List<String> dates = new ArrayList<>();
    private final List<Float> values = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    @Nullable
    private Float avg;
    @Nullable
    private String avgLabel;
    private boolean bars;
    private boolean showMin;
    private boolean touchEnabled = true;
    @Nullable
    private Formatter formatter;
    private float progress = 1f;
    private int touchIndex = -1;
    @Nullable
    private ValueAnimator animator;

    public TrendChartView(@NonNull Context context) {
        this(context, null);
    }

    public TrendChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        primary = ChartKit.color(this, androidx.appcompat.R.attr.colorPrimary);
        surface = ChartKit.color(this, com.google.android.material.R.attr.colorSurfaceContainerLowest);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(ChartKit.dp(context, 2.5f));
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(primary);
        area.setStyle(Paint.Style.FILL);
        area.setColor(ChartKit.alpha(primary, .08f));
        grid.setStrokeWidth(ChartKit.dp(context, 1));
        grid.setColor(ChartKit.color(this, com.google.android.material.R.attr.colorOutlineVariant));
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setStrokeWidth(ChartKit.dp(context, 1));
        avgPaint.setColor(ChartKit.color(this, com.google.android.material.R.attr.colorOutline));
        float d4 = ChartKit.dp(context, 4);
        avgPaint.setPathEffect(new DashPathEffect(new float[]{d4, d4}, 0));
        axisText = ChartKit.text(this, 12, com.google.android.material.R.attr.colorOnSurfaceVariant);
        bubbleText = ChartKit.text(this, 12, com.google.android.material.R.attr.colorOnSurfaceInverse);
        bubbleBg.setColor(ChartKit.color(this, com.google.android.material.R.attr.colorSurfaceInverse));
    }

    public void setFormatter(@Nullable Formatter f) {
        formatter = f;
    }

    public void setTouchEnabled(boolean enabled) {
        touchEnabled = enabled;
    }

    /**
     * @param ds     日期（与 vs 一一对应）
     * @param vs     数值，null 为当天没有数据
     * @param avgVal 平均线（null 不画）
     */
    public void setData(@NonNull List<String> ds, @NonNull List<Float> vs, @Nullable Float avgVal,
                        @Nullable String avgText, boolean asBars, boolean markMin, @Nullable List<Mark> ms) {
        dates.clear();
        dates.addAll(ds);
        values.clear();
        values.addAll(vs);
        avg = avgVal;
        avgLabel = avgText;
        bars = asBars;
        showMin = markMin;
        marks.clear();
        if (ms != null) {
            marks.addAll(ms);
        }
        touchIndex = -1;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f).setDuration(350);
        animator.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void clear() {
        setData(Collections.emptyList(), Collections.emptyList(), null, null, false, false, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = (int) ChartKit.dp(getContext(), 148);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    // ===== 坐标 =====

    private float minV;
    private float maxV;
    private float left;
    private float top;
    private float right;
    private float bottom;

    private boolean computeRange() {
        boolean any = false;
        minV = Float.MAX_VALUE;
        maxV = -Float.MAX_VALUE;
        for (Float v : values) {
            if (v != null) {
                any = true;
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
        }
        if (!any) {
            return false;
        }
        if (bars) {
            minV = Math.min(0, minV);
        }
        if (maxV - minV < 1e-6f) {
            float pad = Math.max(Math.abs(maxV) * .05f, 1f);
            maxV += pad;
            minV -= pad;
        }
        return true;
    }

    private float x(int i) {
        if (values.size() <= 1) {
            return (left + right) / 2f;
        }
        if (bars) {
            float slot = (right - left) / values.size();
            return left + slot * (i + .5f);
        }
        return left + (right - left) * i / (values.size() - 1);
    }

    private float y(float v) {
        return bottom - (bottom - top) * (v - minV) / (maxV - minV);
    }

    @NonNull
    private String axis(float v) {
        return formatter == null ? ChartKit.scale(v, 2) : formatter.axis(v);
    }

    @NonNull
    private String date(int i) {
        String d = dates.get(i);
        return formatter == null ? d : formatter.date(d);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Context c = getContext();
        if (!computeRange()) {
            return;
        }
        float textH = axisText.getTextSize();
        float labelW = Math.max(axisText.measureText(axis(maxV)), axisText.measureText(axis(minV)));
        left = getPaddingLeft() + labelW + ChartKit.dp(c, 8);
        right = getWidth() - getPaddingRight() - ChartKit.dp(c, 8);
        top = getPaddingTop() + textH;
        bottom = getHeight() - getPaddingBottom() - textH - ChartKit.dp(c, 12);
        // 网格与 Y 刻度
        float[] ticks = {maxV, (maxV + minV) / 2f, minV};
        axisText.setTextAlign(Paint.Align.RIGHT);
        for (float t : ticks) {
            float yy = y(t);
            canvas.drawLine(left, yy, right, yy, grid);
            canvas.drawText(axis(t), left - ChartKit.dp(c, 8), yy + textH / 3f, axisText);
        }
        // X 日期：起、中、止
        if (!dates.isEmpty()) {
            float by = getHeight() - getPaddingBottom() - ChartKit.dp(c, 2);
            axisText.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(date(0), left, by, axisText);
            if (dates.size() > 2) {
                axisText.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(date(dates.size() / 2), (left + right) / 2f, by, axisText);
            }
            if (dates.size() > 1) {
                axisText.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(date(dates.size() - 1), right, by, axisText);
            }
        }
        canvas.save();
        canvas.clipRect(0, 0, left + (right - left) * progress + ChartKit.dp(c, 8), getHeight());
        if (bars) {
            drawBars(canvas);
        } else {
            drawLine(canvas);
        }
        if (avg != null) {
            float ay = y(avg);
            path.reset();
            path.moveTo(left, ay);
            path.lineTo(right, ay);
            canvas.drawPath(path, avgPaint);
            if (avgLabel != null) {
                axisText.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(avgLabel, left + ChartKit.dp(c, 4), ay - ChartKit.dp(c, 4), axisText);
            }
        }
        drawMarks(canvas);
        canvas.restore();
        drawTouch(canvas);
    }

    private void drawLine(@NonNull Canvas canvas) {
        Context c = getContext();
        // 面积：每段连续数据单独闭合
        int i = 0;
        while (i < values.size()) {
            while (i < values.size() && values.get(i) == null) {
                i++;
            }
            int start = i;
            while (i < values.size() && values.get(i) != null) {
                i++;
            }
            int end = i - 1;
            if (start > end) {
                continue;
            }
            path.reset();
            path.moveTo(x(start), bottom);
            for (int k = start; k <= end; k++) {
                path.lineTo(x(k), y(values.get(k)));
            }
            path.lineTo(x(end), bottom);
            path.close();
            canvas.drawPath(path, area);
            path.reset();
            path.moveTo(x(start), y(values.get(start)));
            for (int k = start + 1; k <= end; k++) {
                path.lineTo(x(k), y(values.get(k)));
            }
            canvas.drawPath(path, line);
        }
        // 最低价空心点、最新价实心点
        int last = -1;
        int min = -1;
        for (int k = 0; k < values.size(); k++) {
            Float v = values.get(k);
            if (v != null) {
                last = k;
                if (min < 0 || v < values.get(min)) {
                    min = k;
                }
            }
        }
        if (showMin && min >= 0 && min != last) {
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(surface);
            canvas.drawCircle(x(min), y(values.get(min)), ChartKit.dp(c, 3.5f), dot);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(ChartKit.dp(c, 2));
            dot.setColor(primary);
            canvas.drawCircle(x(min), y(values.get(min)), ChartKit.dp(c, 3.5f), dot);
        }
        if (last >= 0) {
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(surface);
            canvas.drawCircle(x(last), y(values.get(last)), ChartKit.dp(c, 6.5f), dot);
            dot.setColor(primary);
            canvas.drawCircle(x(last), y(values.get(last)), ChartKit.dp(c, 4.5f), dot);
        }
    }

    private void drawBars(@NonNull Canvas canvas) {
        float half = ChartKit.dp(getContext(), 3);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(primary);
        for (int k = 0; k < values.size(); k++) {
            Float v = values.get(k);
            if (v == null) {
                continue;
            }
            float xx = x(k);
            rect.set(xx - half, y(v), xx + half, y(Math.max(minV, 0)));
            canvas.drawRoundRect(rect, half, half, dot);
        }
    }

    private void drawMarks(@NonNull Canvas canvas) {
        axisText.setTextAlign(Paint.Align.CENTER);
        float mid = (top + bottom) / 2f;
        for (Mark m : marks) {
            if (m.index < 0 || m.index >= values.size() || values.get(m.index) == null) {
                continue;
            }
            float yy = y(values.get(m.index));
            // 标注放在拐点外侧（偏上的点标在下方，偏下的点标在上方），避开折线
            float ty = yy < mid ? yy + axisText.getTextSize() + ChartKit.dp(getContext(), 6)
                    : yy - ChartKit.dp(getContext(), 6);
            float tx = Math.max(left + axisText.measureText(m.text) / 2f,
                    Math.min(right - axisText.measureText(m.text) / 2f, x(m.index)));
            canvas.drawText(m.text, tx, ty, axisText);
        }
    }

    private void drawTouch(@NonNull Canvas canvas) {
        if (touchIndex < 0 || touchIndex >= values.size() || values.get(touchIndex) == null || formatter == null) {
            return;
        }
        Context c = getContext();
        float xx = x(touchIndex);
        canvas.drawLine(xx, top, xx, bottom, grid);
        String text = formatter.bubble(dates.get(touchIndex), values.get(touchIndex));
        float pad = ChartKit.dp(c, 8);
        float w = bubbleText.measureText(text) + pad * 2;
        float h = bubbleText.getTextSize() + pad * 1.5f;
        float bx = Math.max(left, Math.min(right - w, xx - w / 2f));
        rect.set(bx, 0, bx + w, h);
        canvas.drawRoundRect(rect, ChartKit.dp(c, 4), ChartKit.dp(c, 4), bubbleBg);
        bubbleText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, bx + pad, h - pad * .9f, bubbleText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!touchEnabled || values.isEmpty()) {
            return super.onTouchEvent(e);
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                touchIndex = nearest(e.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                touchIndex = -1;
                invalidate();
                performClick();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int nearest(float px) {
        int best = -1;
        float dist = Float.MAX_VALUE;
        for (int k = 0; k < values.size(); k++) {
            if (values.get(k) == null) {
                continue;
            }
            float d = Math.abs(x(k) - px);
            if (d < dist) {
                dist = d;
                best = k;
            }
        }
        return best;
    }
}
