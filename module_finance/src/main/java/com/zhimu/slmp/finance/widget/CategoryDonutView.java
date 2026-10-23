package com.zhimu.slmp.finance.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * E02 分类占比环形图（UI 设计文档 2.2）：外径 120 dp、环宽 18 dp，自 12 点方向顺时针按金额从大到小，扇区之间 2 dp 白缝；
 * 第 1–6 个扇区为 primary 100%、75%、55%、40%、28%、18% 透明度；中心两行文字。setData 后 350 毫秒重绘；
 * 点按按角度命中扇区，回调 {@link OnSliceClickListener}。
 */
public class CategoryDonutView extends View {

    /** 扇区透明度（第 7 个起沿用最后一档） */
    public static final float[] ALPHAS = {1f, .75f, .55f, .40f, .28f, .18f};

    public interface OnSliceClickListener {
        void onSliceClick(int index);
    }

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint;
    private final Paint labelPaint;
    private final RectF oval = new RectF();
    private final List<Float> values = new ArrayList<>();
    private final float ringWidth;
    private final float gapPx;
    private final int primary;
    private String centerValue = "";
    private String centerLabel = "";
    private int selected = -1;
    private float progress = 1f;
    @Nullable
    private ValueAnimator animator;
    @Nullable
    private OnSliceClickListener listener;

    public CategoryDonutView(@NonNull Context context) {
        this(context, null);
    }

    public CategoryDonutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringWidth = ChartKit.dp(context, 18);
        gapPx = ChartKit.dp(context, 2);
        primary = ChartKit.color(this, androidx.appcompat.R.attr.colorPrimary);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(ringWidth);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);
        valuePaint = ChartKit.text(this, 18, com.google.android.material.R.attr.colorOnSurface);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        labelPaint = ChartKit.text(this, 12, com.google.android.material.R.attr.colorOnSurfaceVariant);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnSliceClickListener(@Nullable OnSliceClickListener l) {
        listener = l;
    }

    /** 数据（已按金额从大到小）与中心文字，350 毫秒自 12 点方向顺时针重绘 */
    public void setData(@NonNull List<Float> amounts, @NonNull String value, @NonNull String label) {
        values.clear();
        values.addAll(amounts);
        centerValue = value;
        centerLabel = label;
        selected = -1;
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

    public void setSelected(int index) {
        selected = index;
        invalidate();
    }

    public static int colorFor(int primary, int index) {
        return ChartKit.alpha(primary, ALPHAS[Math.min(index, ALPHAS.length - 1)]);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) ChartKit.dp(getContext(), 120);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = size / 2f - ringWidth / 2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        float total = 0;
        for (Float v : values) {
            total += v;
        }
        if (total <= 0) {
            arcPaint.setColor(ChartKit.alpha(primary, .12f));
            canvas.drawArc(oval, 0, 360, false, arcPaint);
        } else {
            float gapDeg = (float) Math.toDegrees(gapPx / r);
            float start = -90f;
            float sweepTotal = 360f * progress;
            for (int i = 0; i < values.size(); i++) {
                float sweep = values.get(i) / total * 360f;
                float visible = Math.min(sweep, Math.max(0, sweepTotal - (start + 90f)));
                if (visible > 0) {
                    float draw = values.size() > 1 ? Math.max(visible - gapDeg, 0.5f) : visible;
                    arcPaint.setColor(colorFor(primary, i));
                    arcPaint.setStrokeWidth(i == selected ? ringWidth + ChartKit.dp(getContext(), 4) : ringWidth);
                    canvas.drawArc(oval, start, draw, false, arcPaint);
                }
                start += sweep;
            }
        }
        Paint.FontMetrics fm = valuePaint.getFontMetrics();
        canvas.drawText(centerValue, cx, cy - fm.descent + ChartKit.dp(getContext(), 2), valuePaint);
        canvas.drawText(centerLabel, cx, cy + ChartKit.dp(getContext(), 16), labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int index = hit(e.getX(), e.getY());
            if (index >= 0 && listener != null) {
                listener.onSliceClick(index);
                performClick();
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** 按角度命中扇区；不在环上返回 -1 */
    int hit(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        double dist = Math.hypot(x - cx, y - cy);
        float outer = Math.min(getWidth(), getHeight()) / 2f;
        if (dist < outer - ringWidth - ChartKit.dp(getContext(), 8) || dist > outer + ChartKit.dp(getContext(), 8)) {
            return -1;
        }
        double deg = Math.toDegrees(Math.atan2(y - cy, x - cx)) + 90;
        if (deg < 0) {
            deg += 360;
        }
        return indexAtAngle(values, (float) deg);
    }

    /** 自 12 点顺时针的角度（0–360）落在第几个扇区 */
    static int indexAtAngle(@NonNull List<Float> values, float deg) {
        float total = 0;
        for (Float v : values) {
            total += v;
        }
        if (total <= 0) {
            return -1;
        }
        float acc = 0;
        for (int i = 0; i < values.size(); i++) {
            acc += values.get(i) / total * 360f;
            if (deg <= acc) {
                return i;
            }
        }
        return values.size() - 1;
    }
}
