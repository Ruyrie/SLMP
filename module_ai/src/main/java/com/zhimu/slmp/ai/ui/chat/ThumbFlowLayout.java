package com.zhimu.slmp.ai.ui.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 提问附图的流式排列：按添加顺序从左到右、放不下换行，每行整体右对齐（提问气泡右对齐），项间距 8 dp。
 */
public class ThumbFlowLayout extends ViewGroup {

    private final int gap;

    public ThumbFlowLayout(@NonNull Context context) {
        this(context, null);
    }

    public ThumbFlowLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(8 * context.getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            maxWidth = Integer.MAX_VALUE;
        }
        int lineWidth = 0;
        int lineHeight = 0;
        int width = 0;
        int height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) {
                continue;
            }
            measureChild(c, widthMeasureSpec, heightMeasureSpec);
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            int needed = lineWidth == 0 ? cw : lineWidth + gap + cw;
            if (needed > maxWidth && lineWidth > 0) {
                width = Math.max(width, lineWidth);
                height += lineHeight + gap;
                lineWidth = cw;
                lineHeight = ch;
            } else {
                lineWidth = needed;
                lineHeight = Math.max(lineHeight, ch);
            }
        }
        width = Math.max(width, lineWidth);
        height += lineHeight;
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxWidth = r - l;
        int start = 0;
        int top = 0;
        while (start < getChildCount()) {
            int end = start;
            int lineWidth = 0;
            int lineHeight = 0;
            while (end < getChildCount()) {
                View c = getChildAt(end);
                if (c.getVisibility() == GONE) {
                    end++;
                    continue;
                }
                int needed = lineWidth == 0 ? c.getMeasuredWidth() : lineWidth + gap + c.getMeasuredWidth();
                if (needed > maxWidth && lineWidth > 0) {
                    break;
                }
                lineWidth = needed;
                lineHeight = Math.max(lineHeight, c.getMeasuredHeight());
                end++;
            }
            int x = maxWidth - lineWidth;
            for (int i = start; i < end; i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) {
                    continue;
                }
                c.layout(x, top, x + c.getMeasuredWidth(), top + c.getMeasuredHeight());
                x += c.getMeasuredWidth() + gap;
            }
            top += lineHeight + gap;
            start = end;
        }
    }
}
