package com.zhimu.slmp.base.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.R;

/**
 * 骨架块（02 号《UI设计规范》7.19 节）：surface-container-highest 的圆角矩形或圆形，透明度缓慢往复变化，
 * 不使用横扫的高光渐变。文字条高度取对应字号的行高减 4 dp，圆角 4 dp。
 * <p>
 * 骨架布局与真实布局同结构：列表项保留卡片外形、40 dp 图标块和两条文字条（见 {@code base_skeleton_list}）。
 * XML 属性：{@code app:skeletonShape="rect|circle"}、{@code app:skeletonCornerRadius}（默认 4 dp）、
 * {@code app:skeletonAnimate}（默认 true）。
 */
public class SkeletonView extends View {

    public static final int SHAPE_RECT = 0;
    public static final int SHAPE_CIRCLE = 1;

    private static final long PULSE_DURATION_MS = 900;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int shape;
    private float cornerRadius;
    private boolean animate;
    @Nullable
    private ObjectAnimator pulse;

    public SkeletonView(@NonNull Context context) {
        this(context, null);
    }

    public SkeletonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cornerRadius = getResources().getDimension(R.dimen.base_radius_xs);
        animate = true;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SkeletonView);
            shape = a.getInt(R.styleable.SkeletonView_skeletonShape, SHAPE_RECT);
            cornerRadius = a.getDimension(R.styleable.SkeletonView_skeletonCornerRadius, cornerRadius);
            animate = a.getBoolean(R.styleable.SkeletonView_skeletonAnimate, true);
            a.recycle();
        }
        paint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHighest));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setShape(int shape) {
        this.shape = shape;
        invalidate();
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        invalidate();
    }

    /** 是否做透明度往复动画（同一屏骨架同时开始，节奏一致） */
    public void setAnimate(boolean animate) {
        this.animate = animate;
        if (animate) {
            startPulse();
        } else {
            stopPulse();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        rect.set(0, 0, getWidth(), getHeight());
        if (shape == SHAPE_CIRCLE) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, paint);
        } else {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animate) {
            startPulse();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (!isAttachedToWindow()) {
            return;
        }
        if (visibility == VISIBLE && animate) {
            startPulse();
        } else {
            stopPulse();
        }
    }

    private void startPulse() {
        if (pulse != null && pulse.isStarted()) {
            return;
        }
        pulse = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0.45f);
        pulse.setDuration(PULSE_DURATION_MS);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f));
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        setAlpha(1f);
    }
}
