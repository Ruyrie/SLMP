package com.zhimu.slmp.feed.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/** 限制最大高度的滚动容器（底部面板内的列表：最高占屏幕 2/3，超出时面板内滚动）。 */
public class MaxHeightScrollView extends NestedScrollView {

    private int maxHeightPx;

    public MaxHeightScrollView(@NonNull Context context) {
        super(context);
    }

    public MaxHeightScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = maxHeightPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int limit = mode == MeasureSpec.UNSPECIFIED ? maxHeightPx : Math.min(size, maxHeightPx);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
