package com.zhimu.slmp.finance.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * E13 批次盈亏对比横向条形图（UI 设计文档 2.2）：每批次一行 28 dp，左侧批次号 84 dp 宽，条形高 16 dp、圆角 4，
 * 长度按盈亏绝对值与本期最大值的比例；盈利 primary、亏损 error；条形末端 8 dp 后写数值。点按某行回调。
 */
public class ProfitBarsView extends View {

    public interface OnRowClickListener {
        void onRowClick(int index);
    }

    /** 一行：批次号、盈亏（元）与显示文字 */
    public static final class Row {
        final String label;
        final float value;
        final String valueText;

        public Row(@NonNull String label, float value, @NonNull String valueText) {
            this.label = label;
            this.value = value;
            this.valueText = valueText;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Paint labelPaint;
    private final Paint valuePaint;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int primary;
    private final int error;
    private final float rowH;
    private final float labelW;
    @Nullable
    private OnRowClickListener listener;

    public ProfitBarsView(@NonNull Context context) {
        this(context, null);
    }

    public ProfitBarsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        labelPaint = ChartKit.text(this, 12, com.google.android.material.R.attr.colorOnSurface);
        valuePaint = ChartKit.text(this, 12, com.google.android.material.R.attr.colorOnSurface);
        primary = ChartKit.color(this, androidx.appcompat.R.attr.colorPrimary);
        error = ChartKit.color(this, androidx.appcompat.R.attr.colorError);
        rowH = ChartKit.dp(context, 28);
        labelW = ChartKit.dp(context, 84);
    }

    public void setRows(@NonNull List<Row> list) {
        rows.clear();
        rows.addAll(list);
        requestLayout();
        invalidate();
    }

    public void setOnRowClickListener(@Nullable OnRowClickListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) (rowH * rows.size()));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float max = 0;
        float maxText = 0;
        for (Row r : rows) {
            max = Math.max(max, Math.abs(r.value));
            maxText = Math.max(maxText, valuePaint.measureText(r.valueText));
        }
        float barStart = labelW + ChartKit.dp(getContext(), 16);
        float barMax = Math.max(getWidth() - barStart - maxText - ChartKit.dp(getContext(), 8), 0);
        float barH = ChartKit.dp(getContext(), 16);
        float radius = ChartKit.dp(getContext(), 4);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float cy = rowH * i + rowH / 2f;
            float baseline = cy + labelPaint.getTextSize() / 3f;
            canvas.drawText(r.label, 0, baseline, labelPaint);
            float w = max <= 0 ? 0 : barMax * Math.abs(r.value) / max;
            w = Math.max(w, ChartKit.dp(getContext(), 2));
            barPaint.setColor(r.value < 0 ? error : primary);
            rect.set(barStart, cy - barH / 2f, barStart + w, cy + barH / 2f);
            canvas.drawRoundRect(rect, radius, radius, barPaint);
            canvas.drawText(r.valueText, barStart + w + ChartKit.dp(getContext(), 8), baseline, valuePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int i = (int) (e.getY() / rowH);
            if (i >= 0 && i < rows.size() && listener != null) {
                listener.onRowClick(i);
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
}
