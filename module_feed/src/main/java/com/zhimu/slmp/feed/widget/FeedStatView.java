package com.zhimu.slmp.feed.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.feed.R;

/**
 * 本模块的指标卡（UI 2.2、2.3 节）：圆角 20 dp、无描边无阴影；三行为“图标 + 标签 / 数字 + 单位 / 辅助文字”。
 * 普通卡白底；重点卡（G01 今日计划）primary-container 底；小卡（G06）标签 12 sp、数字 24 sp。
 * 布局：{@code feed_view_stat}；属性 {@code app:feedStatSmall}、{@code app:feedStatEmphasis}。
 */
public class FeedStatView extends MaterialCardView {

    private final ImageView icon;
    private final TextView label;
    private final TextView value;
    private final TextView unit;
    private final TextView helper;
    private boolean emphasis;

    public FeedStatView(@NonNull Context context) {
        this(context, null);
    }

    public FeedStatView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
        LayoutInflater.from(context).inflate(R.layout.feed_view_stat, this, true);
        icon = findViewById(R.id.feed_stat_icon);
        label = findViewById(R.id.feed_stat_label);
        value = findViewById(R.id.feed_stat_value);
        unit = findViewById(R.id.feed_stat_unit);
        helper = findViewById(R.id.feed_stat_helper);
        setCardElevation(0f);
        setStrokeWidth(0);
        setRadius(getResources().getDimension(com.zhimu.slmp.base.R.dimen.base_radius_lg_plus));
        boolean small = false;
        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FeedStatView);
            small = a.getBoolean(R.styleable.FeedStatView_feedStatSmall, false);
            emphasis = a.getBoolean(R.styleable.FeedStatView_feedStatEmphasis, false);
            a.recycle();
        }
        int pad = getResources().getDimensionPixelSize(small ? com.zhimu.slmp.base.R.dimen.base_space_3
                : com.zhimu.slmp.base.R.dimen.base_space_4);
        setContentPadding(pad, pad, pad, pad);
        if (small) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        }
        applyColors(false, false);
    }

    /** 显示指标；card 为空时显示占位“—” */
    public void bind(@Nullable FeedStatCard card) {
        if (card == null) {
            label.setText(null);
            value.setText("—");
            unit.setVisibility(GONE);
            helper.setVisibility(GONE);
            icon.setVisibility(GONE);
            applyColors(false, false);
            return;
        }
        label.setText(card.label);
        value.setText(card.value);
        unit.setText(card.unit);
        unit.setVisibility(TextUtils.isEmpty(card.unit) ? GONE : VISIBLE);
        helper.setText(card.helper);
        helper.setVisibility(TextUtils.isEmpty(card.helper) ? GONE : VISIBLE);
        if (card.icon != 0) {
            icon.setImageResource(card.icon);
            icon.setVisibility(VISIBLE);
        } else {
            icon.setVisibility(GONE);
        }
        applyColors(card.valueError, card.helperError);
        setContentDescription(card.label + " " + card.value + (card.unit == null ? "" : card.unit)
                + (card.helper == null ? "" : "，" + card.helper));
    }

    public void setEmphasis(boolean emphasis) {
        this.emphasis = emphasis;
        applyColors(false, false);
    }

    private void applyColors(boolean valueError, boolean helperError) {
        int bg = MaterialColors.getColor(this, emphasis
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLowest);
        setCardBackgroundColor(bg);
        int strong = MaterialColors.getColor(this, emphasis
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurface);
        int weak = MaterialColors.getColor(this, emphasis
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        int error = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError);
        label.setTextColor(weak);
        icon.setImageTintList(ColorStateList.valueOf(weak));
        value.setTextColor(valueError ? error : strong);
        unit.setTextColor(weak);
        helper.setTextColor(helperError ? error : weak);
    }

    @Override
    public void setOnClickListener(@Nullable View.OnClickListener l) {
        super.setOnClickListener(l);
        setClickable(l != null);
    }
}
