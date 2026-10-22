package com.zhimu.slmp.media.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 可缩放图片（I09 全屏预览）：按比例适配到视图中央，双指缩放 1–4 倍、双击在 1 倍与 2.5 倍之间切换、放大后拖动；
 * 放大后拖到左右边缘时把手势交还给 ViewPager2 翻页。单击触发 {@link #setOnClickListener} 设置的点击（显示或隐藏顶栏）。
 */
public class ZoomableImageView extends AppCompatImageView {

    public static final float MIN_SCALE = 1f;
    public static final float MAX_SCALE = 4f;
    public static final float DOUBLE_TAP_SCALE = 2.5f;

    private final Matrix baseMatrix = new Matrix();
    private final Matrix userMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    @Nullable
    private ValueAnimator animator;
    /** 图片左右边缘是否贴住视图（不能再往该方向拖） */
    private boolean atLeftEdge = true;
    private boolean atRightEdge = true;

    public ZoomableImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        super.setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float target = clamp(getScale() * factor, MIN_SCALE * 0.8f, MAX_SCALE * 1.2f);
                factor = target / getScale();
                userMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                applyMatrix(false);
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                float scale = getScale();
                if (scale < MIN_SCALE || scale > MAX_SCALE) {
                    animateScale(clamp(scale, MIN_SCALE, MAX_SCALE), detector.getFocusX(), detector.getFocusY());
                }
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                return performClick();
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                float target = getScale() > MIN_SCALE + 0.01f ? MIN_SCALE : DOUBLE_TAP_SCALE;
                animateScale(target, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                if (scaleDetector.isInProgress() || getScale() <= MIN_SCALE + 0.01f) {
                    return false;
                }
                userMatrix.postTranslate(-dx, -dy);
                applyMatrix(true);
                // 拖到边缘还继续往外拖：交给 ViewPager2 翻页
                boolean towardRight = dx < 0;
                if ((towardRight && atLeftEdge) || (!towardRight && atRightEdge)) {
                    disallowParentIntercept(false);
                }
                return true;
            }
        });
    }

    @Override
    public void setScaleType(@Nullable ScaleType scaleType) {
        // 固定为 MATRIX
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        //noinspection ConstantValue 父类构造方法中调用时字段尚未初始化
        if (userMatrix != null) {
            resetZoom();
        }
    }

    /** 恢复为适配视图的 1 倍 */
    public void resetZoom() {
        if (animator != null) {
            animator.cancel();
        }
        userMatrix.reset();
        updateBaseMatrix();
        applyMatrix(true);
    }

    public float getScale() {
        userMatrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBaseMatrix();
        applyMatrix(true);
    }

    @SuppressLint("ClickableViewAccessibility") // 单击经 GestureDetector 调用 performClick
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (getDrawable() == null) {
            return super.onTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // 放大状态下先由本视图处理拖动；1 倍时左右滑动交给 ViewPager2
            disallowParentIntercept(getScale() > MIN_SCALE + 0.01f);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            disallowParentIntercept(true);
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            disallowParentIntercept(false);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (getScale() <= MIN_SCALE + 0.01f) {
            return false;
        }
        return direction < 0 ? !atLeftEdge : !atRightEdge;
    }

    private void disallowParentIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void animateScale(float target, float focusX, float focusY) {
        if (animator != null) {
            animator.cancel();
        }
        final float start = getScale();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            float wanted = start + (target - start) * f;
            float factor = wanted / getScale();
            userMatrix.postScale(factor, factor, focusX, focusY);
            applyMatrix(true);
        });
        animator.start();
    }

    /** 图片按比例缩放到视图内并居中（fitCenter） */
    private void updateBaseMatrix() {
        baseMatrix.reset();
        Drawable d = getDrawable();
        int vw = getWidth() - getPaddingLeft() - getPaddingRight();
        int vh = getHeight() - getPaddingTop() - getPaddingBottom();
        if (d == null || vw <= 0 || vh <= 0) {
            return;
        }
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) {
            return;
        }
        float scale = Math.min(vw / (float) dw, vh / (float) dh);
        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate(getPaddingLeft() + (vw - dw * scale) / 2f, getPaddingTop() + (vh - dh * scale) / 2f);
    }

    /** 合成矩阵；checkBounds 时保证图片小于视图时居中、大于视图时不露出空白 */
    private void applyMatrix(boolean checkBounds) {
        Drawable d = getDrawable();
        if (d == null) {
            setImageMatrix(null);
            return;
        }
        if (checkBounds) {
            drawMatrix.set(baseMatrix);
            drawMatrix.postConcat(userMatrix);
            rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            drawMatrix.mapRect(rect);
            float vw = getWidth();
            float vh = getHeight();
            float dx = 0;
            float dy = 0;
            if (rect.width() <= vw) {
                dx = (vw - rect.width()) / 2f - rect.left;
                atLeftEdge = true;
                atRightEdge = true;
            } else {
                if (rect.left > 0) {
                    dx = -rect.left;
                } else if (rect.right < vw) {
                    dx = vw - rect.right;
                }
                atLeftEdge = rect.left + dx >= -0.5f;
                atRightEdge = rect.right + dx <= vw + 0.5f;
            }
            if (rect.height() <= vh) {
                dy = (vh - rect.height()) / 2f - rect.top;
            } else if (rect.top > 0) {
                dy = -rect.top;
            } else if (rect.bottom < vh) {
                dy = vh - rect.bottom;
            }
            userMatrix.postTranslate(dx, dy);
        }
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(userMatrix);
        setImageMatrix(drawMatrix);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
