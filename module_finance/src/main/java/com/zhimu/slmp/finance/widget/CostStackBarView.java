package com.zhimu.slmp.finance.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * E10 成本构成堆叠条（UI 设计文档 2.2）：高 16 dp，每段圆角 4、段间 2 dp 白缝，宽度按金额占比，顺序固定（饲料、引种、兽药疫苗、其他费用）；
 * 金额为 0 的段不画。
 */
public class CostStackBarView extends View {

    /** 一段：金额与颜色 */
    public static final class Segment {
        final float amount;
        final int color;

        public Segment(float amount, int color) {
            this.amount = amount;
            this.color = color;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Segment> segments = new ArrayList<>();
    private final float radius;
    private final float gap;

    public CostStackBarView(@NonNull Context context) {
        this(context, null);
    }

    public CostStackBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        radius = ChartKit.dp(context, 4);
        gap = ChartKit.dp(context, 2);
    }

    public void setSegments(@NonNull List<Segment> list) {
        segments.clear();
        for (Segment s : list) {
            if (s.amount > 0) {
                segments.add(s);
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) ChartKit.dp(getContext(), 16));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float total = 0;
        for (Segment s : segments) {
            total += s.amount;
        }
        if (total <= 0) {
            paint.setColor(ChartKit.color(this, com.google.android.material.R.attr.colorSurfaceContainerHighest));
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, radius, radius, paint);
            return;
        }
        float usable = getWidth() - gap * (segments.size() - 1);
        float x = 0;
        for (Segment s : segments) {
            float w = usable * s.amount / total;
            paint.setColor(s.color);
            rect.set(x, 0, x + w, getHeight());
            canvas.drawRoundRect(rect, radius, radius, paint);
            x += w + gap;
        }
    }
}
