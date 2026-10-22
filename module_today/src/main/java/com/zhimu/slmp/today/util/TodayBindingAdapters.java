package com.zhimu.slmp.today.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.google.android.material.card.MaterialCardView;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.model.vo.CalendarDayVO;
import com.zhimu.slmp.today.ui.calendar.MonthCalendarView;

import java.time.LocalDate;
import java.util.Map;

/**
 * 本组件的绑定适配与显示工具：数字千分位、AI 横幅文字、图标底色调、分组圆角、月历控件数据。
 */
public final class TodayBindingAdapters {

    private TodayBindingAdapters() {
    }

    /** 指标数字：千分位 */
    @NonNull
    public static String count(int value) {
        return NumberFormatter.formatNumber(value);
    }

    /** AI 横幅：“问问 AI 养殖助手 · 推荐问题”，取不到时只写“问问 AI 养殖助手” */
    @NonNull
    public static String aiText(@NonNull Context context, @Nullable String hint) {
        if (Texts.isBlank(hint)) {
            return context.getString(R.string.today_home_ai_default);
        }
        return context.getString(R.string.today_home_ai_with_hint, hint);
    }

    /** 前置图标底与图标颜色（ListRowView.TONE_*） */
    public static void applyTone(@NonNull ImageView icon, int tone) {
        int bg;
        int fg;
        switch (tone) {
            case ListRowView.TONE_ERROR:
                bg = com.google.android.material.R.attr.colorErrorContainer;
                fg = com.google.android.material.R.attr.colorOnErrorContainer;
                break;
            case ListRowView.TONE_WARNING:
                bg = com.zhimu.slmp.base.R.attr.colorWarningContainer;
                fg = com.zhimu.slmp.base.R.attr.colorOnWarningContainer;
                break;
            case ListRowView.TONE_TERTIARY:
                bg = com.google.android.material.R.attr.colorTertiaryContainer;
                fg = com.google.android.material.R.attr.colorOnTertiaryContainer;
                break;
            case ListRowView.TONE_NEUTRAL:
                bg = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                fg = com.google.android.material.R.attr.colorOnSurfaceVariant;
                break;
            case ListRowView.TONE_PRIMARY:
                bg = com.google.android.material.R.attr.colorPrimaryContainer;
                fg = com.google.android.material.R.attr.colorOnPrimaryContainer;
                break;
            case ListRowView.TONE_SECONDARY:
            default:
                bg = com.google.android.material.R.attr.colorSecondaryContainer;
                fg = com.google.android.material.R.attr.colorOnSecondaryContainer;
                break;
        }
        icon.setBackgroundTintList(ColorStateList.valueOf(color(icon, bg)));
        icon.setImageTintList(ColorStateList.valueOf(color(icon, fg)));
    }

    public static int color(@NonNull View view, @AttrRes int attr) {
        return MaterialColors.getColor(view, attr);
    }

    /** 分组列表项：圆角按位置，项间 2 dp（首项不留），左右边距可选 */
    public static void applyGroup(@NonNull MaterialCardView card, int index, int size, boolean pageMargin) {
        applyGroupPosition(card, GroupPosition.of(index, size), pageMargin);
    }

    /** 按 GroupPosition 设置圆角与边距 */
    public static void applyGroupPosition(@NonNull MaterialCardView card, int position, boolean pageMargin) {
        GroupPosition.apply(card, position);
        if (card.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) card.getLayoutParams();
            int margin = pageMargin ? card.getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_page_margin) : 0;
            int gap = position == GroupPosition.TOP || position == GroupPosition.SINGLE
                    ? 0 : card.getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_list_gap);
            if (lp.leftMargin != margin || lp.rightMargin != margin || lp.topMargin != gap) {
                lp.leftMargin = margin;
                lp.rightMargin = margin;
                lp.topMargin = gap;
                card.setLayoutParams(lp);
            }
        }
    }

    // ===================== MonthCalendarView =====================

    @BindingAdapter("calendarDays")
    public static void setCalendarDays(@NonNull MonthCalendarView view, @Nullable Map<LocalDate, CalendarDayVO> days) {
        view.setDays(days);
    }

    @BindingAdapter("calendarSelected")
    public static void setCalendarSelected(@NonNull MonthCalendarView view, @Nullable LocalDate date) {
        if (date != null) {
            view.setSelectedDate(date);
        }
    }

    @BindingAdapter("calendarLoading")
    public static void setCalendarLoading(@NonNull MonthCalendarView view, @Nullable Boolean loading) {
        view.setLoading(Boolean.TRUE.equals(loading));
    }
}
