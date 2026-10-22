package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textview.MaterialTextView;
import com.zhimu.slmp.base.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 指标卡 StatCard（02 号《UI设计规范》7.8 节）：标签（可带 20 dp 图标）→ 大数字 + 单位 → 环比行或分项指标。
 * <ul>
 *     <li>紧凑型 {@link #SIZE_COMPACT}：高 84 dp，标签 body-m 14 sp，数字 28 sp + 单位 14 sp（三张一行时 143 / 111 / 110 dp）</li>
 *     <li>大型 {@link #SIZE_LARGE}：整宽，数字 display-s 36 sp + 单位 16 sp；下方 1 dp outline-variant 分隔线后为三列分项
 *     （标签 body-s 12 sp + 值 16 sp），用 {@link #addSubItem(CharSequence, CharSequence)} 添加</li>
 * </ul>
 * 重点卡（{@code app:statEmphasis="true"}）为 primary-container 底，文字、图标全部 on-primary-container；
 * 普通卡 surface-container-lowest 底。圆角 20 dp，无阴影无描边，数字用等宽字形。
 * 环比颜色按指标含义选（{@link #TREND_GOOD} success、{@link #TREND_BAD} error、{@link #TREND_NEUTRAL} on-surface-variant），
 * 始终带 {@code arrow_upward} / {@code arrow_downward} 和文字。整卡可点击进入明细时设置点击监听，显示水波纹。
 */
public class StatCardView extends MaterialCardView {

    public static final int SIZE_COMPACT = 0;
    public static final int SIZE_LARGE = 1;

    public static final int TREND_NONE = 0;
    public static final int TREND_GOOD = 1;
    public static final int TREND_BAD = 2;
    public static final int TREND_NEUTRAL = 3;

    public static final int DIRECTION_NONE = 0;
    public static final int DIRECTION_UP = 1;
    public static final int DIRECTION_DOWN = 2;

    @IntDef({TREND_NONE, TREND_GOOD, TREND_BAD, TREND_NEUTRAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Trend {
    }

    @IntDef({DIRECTION_NONE, DIRECTION_UP, DIRECTION_DOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction {
    }

    private final LinearLayout root;
    private final ImageView icon;
    private final TextView label;
    private final TextView value;
    private final TextView unit;
    private final View trendRow;
    private final ImageView trendIcon;
    private final TextView trendText;
    private final View divider;
    private final LinearLayout subItems;

    private boolean emphasis;
    private int size = SIZE_COMPACT;
    private int trend = TREND_NONE;
    private int direction = DIRECTION_NONE;

    public StatCardView(@NonNull Context context) {
        this(context, null);
    }

    public StatCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public StatCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.base_view_stat_card, this, true);
        root = findViewById(R.id.base_stat_root);
        icon = findViewById(R.id.base_stat_icon);
        label = findViewById(R.id.base_stat_label);
        value = findViewById(R.id.base_stat_value);
        unit = findViewById(R.id.base_stat_unit);
        trendRow = findViewById(R.id.base_stat_trend);
        trendIcon = findViewById(R.id.base_stat_trend_icon);
        trendText = findViewById(R.id.base_stat_trend_text);
        divider = findViewById(R.id.base_stat_divider);
        subItems = findViewById(R.id.base_stat_sub_items);

        setCardElevation(0f);
        setStrokeWidth(0);
        setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCornerSizes(getResources().getDimension(R.dimen.base_radius_lg_plus))
                .build());

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StatCardView);
            label.setText(a.getText(R.styleable.StatCardView_statLabel));
            int iconRes = a.getResourceId(R.styleable.StatCardView_statIcon, 0);
            value.setText(a.getText(R.styleable.StatCardView_statValue));
            setUnit(a.getText(R.styleable.StatCardView_statUnit));
            CharSequence trendTextValue = a.getText(R.styleable.StatCardView_statTrendText);
            trend = a.getInt(R.styleable.StatCardView_statTrend, TREND_NONE);
            direction = a.getInt(R.styleable.StatCardView_statTrendDirection, DIRECTION_NONE);
            emphasis = a.getBoolean(R.styleable.StatCardView_statEmphasis, false);
            size = a.getInt(R.styleable.StatCardView_statSize, SIZE_COMPACT);
            a.recycle();
            setIcon(iconRes);
            trendText.setText(trendTextValue);
        }
        applySize();
        applyColors();
    }

    public void setLabel(@Nullable CharSequence text) {
        label.setText(text);
    }

    /** 标签前 20 dp 图标（如存栏 {@code fence}），0 为不显示 */
    public void setIcon(@DrawableRes int iconRes) {
        if (iconRes == 0) {
            icon.setVisibility(GONE);
        } else {
            icon.setImageResource(iconRes);
            icon.setVisibility(VISIBLE);
        }
    }

    /** 数字；分母为 0 等特殊情况传“无数据”（R.string.base_stat_no_data） */
    public void setValue(@Nullable CharSequence text) {
        value.setText(text);
    }

    public void setUnit(@Nullable CharSequence text) {
        unit.setText(text);
        unit.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    /** 同时设置数字与单位 */
    public void setValue(@Nullable CharSequence valueText, @Nullable CharSequence unitText) {
        setValue(valueText);
        setUnit(unitText);
    }

    /**
     * 环比行（“较上月同期 +32.1%”）。
     *
     * @param text      文字，为空时隐藏整行
     * @param trend     好坏：{@link #TREND_GOOD}、{@link #TREND_BAD}、{@link #TREND_NEUTRAL}
     * @param direction 箭头：{@link #DIRECTION_UP}、{@link #DIRECTION_DOWN}、{@link #DIRECTION_NONE}
     */
    public void setTrend(@Nullable CharSequence text, @Trend int trend, @Direction int direction) {
        trendText.setText(text);
        this.trend = trend;
        this.direction = direction;
        applyColors();
    }

    /** 重点卡（primary-container 底） */
    public void setEmphasis(boolean emphasis) {
        this.emphasis = emphasis;
        applyColors();
    }

    public void setSize(int size) {
        this.size = size;
        applySize();
        applyColors();
    }

    /** 大型卡的分项指标（最多三列），如“在养批次 / 4 个” */
    public void addSubItem(@NonNull CharSequence subLabel, @NonNull CharSequence subValue) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        TextView l = new MaterialTextView(getContext());
        l.setTextAppearance(R.style.TextAppearance_Zmt_BodySmall);
        l.setText(subLabel);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        TextView v = new MaterialTextView(getContext());
        v.setTextAppearance(R.style.TextAppearance_Zmt_BodyLarge_Number);
        v.setText(subValue);
        v.setSingleLine(true);
        column.addView(l);
        column.addView(v);
        subItems.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        subItems.setVisibility(VISIBLE);
        divider.setVisibility(VISIBLE);
        applyColors();
    }

    public void clearSubItems() {
        subItems.removeAllViews();
        subItems.setVisibility(GONE);
        divider.setVisibility(GONE);
    }

    private void applySize() {
        if (size == SIZE_LARGE) {
            value.setTextAppearance(R.style.TextAppearance_Zmt_DisplaySmall_Number);
            unit.setTextAppearance(R.style.TextAppearance_Zmt_BodyLarge);
            int p = getResources().getDimensionPixelSize(R.dimen.base_space_4);
            root.setPadding(p, p, p, p);
            setMinimumHeight(0);
        } else {
            value.setTextAppearance(R.style.TextAppearance_Zmt_HeadlineMedium_Number);
            unit.setTextAppearance(R.style.TextAppearance_Zmt_BodyMedium);
            int h = getResources().getDimensionPixelSize(R.dimen.base_space_4);
            int v = getResources().getDimensionPixelSize(R.dimen.base_space_3);
            root.setPadding(h, v, h, v);
            setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.base_stat_card_compact_height));
        }
    }

    private void applyColors() {
        int bg = MaterialColors.getColor(this, emphasis
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLowest);
        int onEmphasis = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
        int onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int labelColor = emphasis ? onEmphasis : onVariant;
        int valueColor = emphasis ? onEmphasis : onSurface;
        setCardBackgroundColor(bg);
        label.setTextColor(labelColor);
        icon.setImageTintList(ColorStateList.valueOf(labelColor));
        value.setTextColor(valueColor);
        unit.setTextColor(labelColor);
        for (int i = 0; i < subItems.getChildCount(); i++) {
            View column = subItems.getChildAt(i);
            if (column instanceof LinearLayout && ((LinearLayout) column).getChildCount() == 2) {
                ((TextView) ((LinearLayout) column).getChildAt(0)).setTextColor(labelColor);
                ((TextView) ((LinearLayout) column).getChildAt(1)).setTextColor(valueColor);
            }
        }

        boolean showTrend = !TextUtils.isEmpty(trendText.getText());
        trendRow.setVisibility(showTrend ? VISIBLE : GONE);
        if (showTrend) {
            int color;
            switch (trend) {
                case TREND_GOOD:
                    color = MaterialColors.getColor(this, R.attr.colorSuccess);
                    break;
                case TREND_BAD:
                    color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError);
                    break;
                default:
                    color = emphasis ? onEmphasis : onVariant;
                    break;
            }
            trendText.setTextColor(color);
            if (direction == DIRECTION_NONE) {
                trendIcon.setVisibility(GONE);
            } else {
                trendIcon.setImageResource(direction == DIRECTION_UP ? R.drawable.ic_arrow_upward
                        : R.drawable.ic_arrow_downward);
                trendIcon.setImageTintList(ColorStateList.valueOf(color));
                trendIcon.setVisibility(VISIBLE);
            }
        }
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        setClickable(l != null);
        setFocusable(l != null);
    }
}
