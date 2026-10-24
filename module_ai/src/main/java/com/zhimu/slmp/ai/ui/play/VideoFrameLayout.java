package com.zhimu.slmp.ai.ui.play;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 视频区（K05 竖屏、K03 内嵌）：高度在测量时按实际宽度取 16:9，不依赖旋转过程中的屏幕尺寸；
 * 全屏（K06）、画中画时关闭比例，按布局参数铺满。
 */
public class VideoFrameLayout extends FrameLayout {

    private boolean keepRatio = true;

    public VideoFrameLayout(@NonNull Context context) {
        super(context);
    }

    public VideoFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** true：高度 = 宽度 × 9 / 16；false：按布局参数（全屏时 match_parent） */
    public void setKeepRatio(boolean keep) {
        if (keepRatio != keep) {
            keepRatio = keep;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (keepRatio && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int height = Math.round(MeasureSpec.getSize(widthMeasureSpec) * 9f / 16f);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
