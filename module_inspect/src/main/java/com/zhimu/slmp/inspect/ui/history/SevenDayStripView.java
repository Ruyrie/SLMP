package com.zhimu.slmp.inspect.ui.history;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.model.vo.PenInspectionOverviewVO;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * I04 近 7 天格（技术文档 SevenDayStripView）：每天一格（日期 + 8 dp 状态点）：异常 error、补录 / 超时 warning、
 * 已巡 primary、漏巡 outline、未巡与无记录 outline-variant；点某天列表定位到该日。
 */
public class SevenDayStripView extends LinearLayout {

    @Nullable
    private Consumer<String> onDayClick;

    public SevenDayStripView(@NonNull Context context) {
        this(context, null);
    }

    public SevenDayStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
    }

    public void setOnDayClick(@Nullable Consumer<String> listener) {
        this.onDayClick = listener;
    }

    public void setDays(@Nullable List<PenInspectionOverviewVO.DayCell> days) {
        removeAllViews();
        if (days == null) {
            return;
        }
        for (PenInspectionOverviewVO.DayCell d : days) {
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_1);
            cell.setPadding(0, pad, 0, pad);
            TextView label = new TextView(getContext());
            LocalDate date = DateFormatter.parseDate(d.getDate());
            label.setText(date == null ? "" : String.valueOf(date.getDayOfMonth()));
            label.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodySmall_Number);
            label.setTextColor(ContextCompat.getColor(getContext(), com.zhimu.slmp.base.R.color.zm_on_surface_variant));
            cell.addView(label);
            View dot = new View(getContext());
            int size = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_2);
            LayoutParams lp = new LayoutParams(size, size);
            lp.topMargin = pad;
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.inspect_bg_day_dot);
            dot.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), colorOf(d.getDayTag())));
            cell.addView(dot);
            cell.setContentDescription(DateFormatter.formatDate(d.getDate(), false) + " " + textOf(d.getDayTag()));
            ClickThrottle.setOnClickListener(cell, v -> {
                if (onDayClick != null) {
                    onDayClick.accept(d.getDate());
                }
            });
            addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private static int colorOf(@Nullable String tag) {
        if ("ABNORMAL".equals(tag)) {
            return com.zhimu.slmp.base.R.color.zm_error;
        }
        if ("BACKFILL".equals(tag) || "OVERDUE".equals(tag)) {
            return com.zhimu.slmp.base.R.color.zm_warning;
        }
        if ("DONE".equals(tag)) {
            return com.zhimu.slmp.base.R.color.zm_primary;
        }
        if ("MISSED".equals(tag)) {
            return com.zhimu.slmp.base.R.color.zm_outline;
        }
        return com.zhimu.slmp.base.R.color.zm_outline_variant;
    }

    @NonNull
    private static String textOf(@Nullable String tag) {
        if (tag == null) {
            return "";
        }
        switch (tag) {
            case "ABNORMAL":
                return "异常";
            case "BACKFILL":
                return "补录";
            case "OVERDUE":
                return "超时";
            case "DONE":
                return "已巡";
            case "MISSED":
                return "漏巡";
            case "PENDING":
                return "未巡";
            default:
                return "无记录";
        }
    }
}
