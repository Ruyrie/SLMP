package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

/**
 * 描边输入框（02 号《UI设计规范》7.9 节）：在 MDC TextInputLayout 基础上，出错时描边与聚焦时一样为 2 dp
 * （MDC 未聚焦出错时只有 1 dp）。布局中代替 {@code com.google.android.material.textfield.TextInputLayout} 使用，
 * 样式、属性与用法不变。
 */
public class ZmtTextInputLayout extends TextInputLayout {

    /** 父类构造过程中可能调用 setError，此时子类字段尚未初始化 */
    private boolean initialized;
    private int defaultStrokeWidth;

    public ZmtTextInputLayout(@NonNull Context context) {
        this(context, null);
    }

    public ZmtTextInputLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.textInputStyle);
    }

    public ZmtTextInputLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        defaultStrokeWidth = getBoxStrokeWidth();
        initialized = true;
        updateStrokeWidth(getError());
    }

    @Override
    public void setError(@Nullable CharSequence errorText) {
        super.setError(errorText);
        updateStrokeWidth(errorText);
    }

    @Override
    public void setBoxStrokeWidth(int boxStrokeWidth) {
        super.setBoxStrokeWidth(boxStrokeWidth);
        if (initialized && TextUtils.isEmpty(getError())) {
            defaultStrokeWidth = boxStrokeWidth;
        }
    }

    private void updateStrokeWidth(@Nullable CharSequence errorText) {
        if (!initialized) {
            return;
        }
        int width = TextUtils.isEmpty(errorText) ? defaultStrokeWidth : getBoxStrokeWidthFocused();
        if (getBoxStrokeWidth() != width) {
            super.setBoxStrokeWidth(width);
        }
    }
}
