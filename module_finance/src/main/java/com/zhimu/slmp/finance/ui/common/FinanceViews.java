package com.zhimu.slmp.finance.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.ZmtTextInputLayout;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.Locale;
import java.util.Map;

/**
 * 模块10 视图工具：环比与涨跌的颜色和箭头、收款状态标签、字段错误、筛选 Chip。
 */
public final class FinanceViews {

    private FinanceViews() {
    }

    public static int color(@NonNull View v, @AttrRes int attr) {
        return MaterialColors.getColor(v, attr);
    }

    @AttrRes
    public static int toneAttr(int tone) {
        switch (tone) {
            case FinanceFormat.TONE_GOOD:
                return R.attr.colorSuccess;
            case FinanceFormat.TONE_BAD:
                return androidx.appcompat.R.attr.colorError;
            default:
                return com.google.android.material.R.attr.colorOnSurfaceVariant;
        }
    }

    /** 环比 / 涨跌文字：颜色按含义；有方向时前置 16 dp 箭头 */
    public static void setTrend(@NonNull TextView tv, @NonNull CharSequence text, int tone, int direction) {
        tv.setText(text);
        int color = color(tv, toneAttr(tone));
        tv.setTextColor(color);
        Drawable d = null;
        if (direction != 0 && tone != FinanceFormat.TONE_NONE && tone != FinanceFormat.TONE_NEUTRAL) {
            d = AppCompatResources.getDrawable(tv.getContext(), direction > 0 ? R.drawable.ic_arrow_upward
                    : R.drawable.ic_arrow_downward);
        }
        if (d != null) {
            int size = (int) (16 * tv.getResources().getDisplayMetrics().density);
            d = d.mutate();
            d.setBounds(0, 0, size, size);
            d.setTint(color);
        }
        tv.setCompoundDrawablesRelative(d, null, null, null);
        tv.setCompoundDrawablePadding((int) (2 * tv.getResources().getDisplayMetrics().density));
    }

    /** 数字 + 小一档的单位“87,600 元” */
    @NonNull
    public static CharSequence amountWithUnit(@NonNull View v, @NonNull String number, @NonNull String unit,
                                              float unitScale) {
        String text = number + " " + unit;
        SpannableString s = new SpannableString(text);
        int start = number.length() + 1;
        s.setSpan(new RelativeSizeSpan(unitScale), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(color(v, com.google.android.material.R.attr.colorOnSurfaceVariant)), start,
                text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** 收款状态标签（E07、E09、E14 一致） */
    public static void bindPaymentTag(@NonNull StatusTagView tag, @NonNull SaleOrderItemVO o) {
        String status = o.getPaymentStatus();
        if (FinanceConst.PAY_PARTIAL.equals(status)) {
            tag.setStatus("部分收款", StatusTagView.PRIMARY);
            tag.setTagIcon(0);
        } else if (FinanceConst.PAY_PAID.equals(status)) {
            tag.setStatus("已收款", StatusTagView.SUCCESS);
            tag.setTagIcon(R.drawable.ic_check_circle);
        } else if (FinanceConst.PAY_VOIDED.equals(status)) {
            tag.setStatus("已作废", StatusTagView.NEUTRAL);
            tag.setTagIcon(0);
        } else {
            tag.setStatus("未收款", StatusTagView.NEUTRAL);
            tag.setTagIcon(0);
        }
    }

    /** 约定收款标签：已逾期 error、3 天内 warning，更远不显示 */
    public static void bindDueTag(@NonNull StatusTagView tag, @NonNull SaleOrderItemVO o) {
        String text = FinanceFormat.dueTag(o);
        if (text == null || FinanceFormat.isVoided(o) || FinanceConst.PAY_PAID.equals(o.getPaymentStatus())) {
            tag.setVisibility(View.GONE);
            return;
        }
        tag.setVisibility(View.VISIBLE);
        boolean overdue = FinanceFormat.isOverdue(o);
        tag.setStatus(text, overdue ? StatusTagView.ERROR : StatusTagView.WARNING);
        tag.setTagIcon(overdue ? R.drawable.ic_schedule : 0);
    }

    /** 字段错误（ZmtTextInputLayout 出错时描边 2 dp）；没有错误时恢复辅助文字 */
    public static void setError(@Nullable ZmtTextInputLayout layout, @Nullable Map<String, String> errors,
                                @NonNull String key) {
        if (layout == null) {
            return;
        }
        String e = errors == null ? null : errors.get(key);
        layout.setError(e);
        layout.setErrorEnabled(e != null);
    }

    public static void strike(@NonNull TextView tv, boolean on) {
        tv.setPaintFlags(on ? tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    /** 筛选 Chip（单选组内），选中时前置 check */
    @NonNull
    public static Chip filterChip(@NonNull Context context, @NonNull ChipGroup group, @NonNull CharSequence text,
                                  @NonNull String tag) {
        Chip c = (Chip) android.view.LayoutInflater.from(context).inflate(
                com.zhimu.slmp.finance.R.layout.finance_item_filter_chip, group, false);
        c.setText(text);
        c.setTag(tag);
        c.setCheckable(true);
        c.setId(View.generateViewId());
        group.addView(c);
        return c;
    }

    /** 关键词命中部分改为 primary 色 */
    @NonNull
    public static CharSequence highlight(@NonNull View v, @Nullable String text, @Nullable String keyword) {
        if (text == null) {
            return "";
        }
        if (keyword == null || keyword.isEmpty()) {
            return text;
        }
        int i = text.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if (i < 0) {
            return text;
        }
        SpannableString s = new SpannableString(text);
        s.setSpan(new ForegroundColorSpan(color(v, androidx.appcompat.R.attr.colorPrimary)), i, i + keyword.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    public static ColorStateList csl(@NonNull View v, @AttrRes int attr) {
        return ColorStateList.valueOf(color(v, attr));
    }
}
