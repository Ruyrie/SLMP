package com.zhimu.slmp.ai.ui.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * “正在思考…”前的三点跳动动画：3 个 6 dp 圆点、间距 4 dp、on-surface-variant，依次上下跳动 4 dp，一个周期 1.2 s。
 * Paint 在构造时创建，onDraw 不分配对象；不在窗口中时停止动画。
 */
public class ThinkingDotsView extends View {

    private static final long CYCLE_MS = 1200;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dot;
    private final float gap;
    private final float jump;
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float phase;

    public ThinkingDotsView(@NonNull Context context) {
        this(context, null);
    }

    public ThinkingDotsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = context.getResources().getDisplayMetrics().density;
        dot = 6 * d;
        gap = 4 * d;
        jump = 4 * d;
        paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) Math.ceil(dot * 3 + gap * 2) + getPaddingLeft() + getPaddingRight();
        int h = (int) Math.ceil(dot + jump * 2) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float r = dot / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < 3; i++) {
            // 每个点相差 1/6 周期，前 1/3 周期内上跳再落下
            float p = (phase - i / 6f + 1f) % 1f;
            float offset = p < 1f / 3f ? (float) Math.sin(p * 3 * Math.PI) * jump : 0f;
            float cx = getPaddingLeft() + r + i * (dot + gap);
            canvas.drawCircle(cx, cy - offset, r, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (!isAttachedToWindow()) {
            return;
        }
        if (isShown()) {
            if (!animator.isStarted()) {
                animator.start();
            }
        } else {
            animator.cancel();
        }
    }
}
