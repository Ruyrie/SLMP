package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

/**
 * 描边输入框（02 号《UI设计规范》7.9 节）：在 MDC TextInputLayout 基础上，出错时描边与聚焦时一样为 2 dp
 * （MDC 未聚焦出错时只有 1 dp）；前缀（如“+86”）、后缀（如“头”）与输入文字基线对齐（MDC 按垂直居中摆放，
 * 前后缀 14 sp、输入值 16 sp 时前后缀会偏高）。布局中代替 {@code com.google.android.material.textfield.TextInputLayout}
 * 使用，样式、属性与用法不变。
 */
public class ZmtTextInputLayout extends TextInputLayout {

    /** 父类构造过程中可能调用 setError，此时子类字段尚未初始化 */
    private boolean initialized;
    private int defaultStrokeWidth;
    private final int[] location = new int[2];

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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        alignBaseline(getPrefixTextView());
        alignBaseline(getSuffixTextView());
    }

    /** 用 translationY 把前缀 / 后缀的基线移到输入文字第一行的基线上 */
    private void alignBaseline(@NonNull TextView affix) {
        EditText edit = getEditText();
        if (edit == null || affix.getVisibility() != VISIBLE
                || affix.getBaseline() < 0 || edit.getBaseline() < 0) {
            return;
        }
        edit.getLocationInWindow(location);
        int editBaseline = location[1] + edit.getBaseline();
        affix.getLocationInWindow(location);
        // getLocationInWindow 已含当前位移，先扣掉再算
        int affixBaseline = location[1] - Math.round(affix.getTranslationY()) + affix.getBaseline();
        float dy = editBaseline - affixBaseline;
        if (affix.getTranslationY() != dy) {
            affix.setTranslationY(dy);
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
