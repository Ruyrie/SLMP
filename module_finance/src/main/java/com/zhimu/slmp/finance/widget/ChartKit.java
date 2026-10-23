package com.zhimu.slmp.finance.widget;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 图表绘制工具（UI 设计文档 2.2“图表”：只用 primary 单色系和少量中性色，网格线 1 dp outline-variant，坐标轴文字 12 sp）。
 */
final class ChartKit {

    private ChartKit() {
    }

    static float dp(@NonNull Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static float sp(@NonNull Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    static int color(@NonNull View v, @AttrRes int attr) {
        return MaterialColors.getColor(v, attr);
    }

    @NonNull
    static Paint text(@NonNull View v, float sizeSp, @AttrRes int colorAttr) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp(v.getContext(), sizeSp));
        p.setColor(color(v, colorAttr));
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    /** 透明度（0–1）叠加到颜色 */
    static int alpha(int color, float a) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 坐标刻度文字：两位小数去掉末尾 0 以外的格式由调用方决定，这里统一 HALF_UP */
    @NonNull
    static String scale(double v, int digits) {
        return BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }
}
