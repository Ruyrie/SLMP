package com.zhimu.slmp.media.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * 扫码取景框（L07 原型）：四角白色圆角折线 + 上下往复的扫描线；{@link #setScanning(boolean)} 控制扫描线动画。
 * 视图本身即取景框区域，建议宽高相等。
 */
public class ScanFrameView extends View {

    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cornerLength;
    private final float cornerRadius;
    private final float stroke;
    private final float lineHeight;
    private final int lineColor;
    @Nullable
    private ValueAnimator animator;
    private float lineFraction;
    private boolean scanning = true;

    public ScanFrameView(@NonNull Context context) {
        this(context, null);
    }

    public ScanFrameView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        stroke = 4 * density;
        cornerLength = 32 * density;
        cornerRadius = 12 * density;
        lineHeight = 2 * density;
        int onMedia = MaterialColors.getColor(context, com.zhimu.slmp.base.R.attr.colorOnMedia, 0xFFFFFFFF);
        lineColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryInverse,
                0xFFA8C7FA);
        cornerPaint.setColor(onMedia);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(stroke);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStyle(Paint.Style.FILL);
    }

    /** 识别中显示扫描线动画；识别成功后停止 */
    public void setScanning(boolean on) {
        if (scanning == on) {
            return;
        }
        scanning = on;
        updateAnimator();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimator();
    }

    private void updateAnimator() {
        boolean run = scanning && isAttachedToWindow() && isShown();
        if (!run) {
            stopAnimator();
            return;
        }
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0.08f, 0.92f);
        animator.setDuration(2200);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            lineFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int transparent = lineColor & 0x00FFFFFF;
        linePaint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{transparent, lineColor, transparent}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float half = stroke / 2;
        float l = half;
        float t = half;
        float r = getWidth() - half;
        float b = getHeight() - half;
        path.reset();
        // 左上
        path.moveTo(l, t + cornerLength);
        path.lineTo(l, t + cornerRadius);
        path.quadTo(l, t, l + cornerRadius, t);
        path.lineTo(l + cornerLength, t);
        // 右上
        path.moveTo(r - cornerLength, t);
        path.lineTo(r - cornerRadius, t);
        path.quadTo(r, t, r, t + cornerRadius);
        path.lineTo(r, t + cornerLength);
        // 右下
        path.moveTo(r, b - cornerLength);
        path.lineTo(r, b - cornerRadius);
        path.quadTo(r, b, r - cornerRadius, b);
        path.lineTo(r - cornerLength, b);
        // 左下
        path.moveTo(l + cornerLength, b);
        path.lineTo(l + cornerRadius, b);
        path.quadTo(l, b, l, b - cornerRadius);
        path.lineTo(l, b - cornerLength);
        canvas.drawPath(path, cornerPaint);

        if (scanning) {
            float y = getHeight() * lineFraction;
            float inset = getWidth() * 0.06f;
            canvas.drawRect(inset, y - lineHeight / 2, getWidth() - inset, y + lineHeight / 2, linePaint);
        }
    }
}
