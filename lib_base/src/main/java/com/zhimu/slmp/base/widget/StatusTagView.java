package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textview.MaterialTextView;
import com.zhimu.slmp.base.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 状态标签（02 号《UI设计规范》3.3、7.6 节）：只做展示，不可点击、没有水波纹。
 * <p>
 * 高 24 dp、圆角 8 dp、左右内边距 8 dp，文字 label-m 12 sp；可带 16 dp 图标（与文字间距 4 dp）。
 * 按语义取色：
 * <ul>
 *     <li>{@link #NEUTRAL}：已完成、已通过、空栏、作业类等（surface-container-highest + on-surface-variant）</li>
 *     <li>{@link #PRIMARY}：待处理、今天、当前、推荐、在养（primary-container）</li>
 *     <li>{@link #WARNING}：关注、临期、库存不足、休药中、待审核、预警（warning-container）</li>
 *     <li>{@link #ERROR}：已逾期、紧急、已过期、已停用、已驳回（error-container）</li>
 *     <li>{@link #SUCCESS}：可出栏、已收款、治愈（success-container）</li>
 *     <li>{@link #TERTIARY}：AI 生成、AI 消息（tertiary-container）</li>
 *     <li>{@link #FARM}：养殖场名称（无底色，1 dp outline-variant 描边）</li>
 * </ul>
 * XML：{@code app:tagType="warning" app:tagIcon="@drawable/ic_hourglass_top" android:text="休药中"}；
 * 代码：{@link #setTagType(int)}、{@link #setTagIcon(int)}、{@link #setStatus(CharSequence, int)}。
 */
public class StatusTagView extends MaterialTextView {

    public static final int NEUTRAL = 0;
    public static final int PRIMARY = 1;
    public static final int WARNING = 2;
    public static final int ERROR = 3;
    public static final int SUCCESS = 4;
    public static final int TERTIARY = 5;
    public static final int FARM = 6;

    @IntDef({NEUTRAL, PRIMARY, WARNING, ERROR, SUCCESS, TERTIARY, FARM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TagType {
    }

    private final MaterialShapeDrawable background;
    private final int iconSize;
    private int tagType = NEUTRAL;
    @Nullable
    private Drawable icon;

    public StatusTagView(@NonNull Context context) {
        this(context, null);
    }

    public StatusTagView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        iconSize = getResources().getDimensionPixelSize(R.dimen.base_icon_size_tag);
        background = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(getResources().getDimension(R.dimen.base_radius_sm))
                .build());
        setBackground(background);
        setTextAppearance(R.style.TextAppearance_Zmt_LabelMedium);
        int padding = getResources().getDimensionPixelSize(R.dimen.base_tag_padding_horizontal);
        setPaddingRelative(padding, 0, padding, 0);
        setMinHeight(getResources().getDimensionPixelSize(R.dimen.base_tag_height));
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.base_tag_height));
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setIncludeFontPadding(false);
        setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.base_space_1));
        setClickable(false);
        setFocusable(false);

        int type = NEUTRAL;
        int iconRes = 0;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StatusTagView);
            type = a.getInt(R.styleable.StatusTagView_tagType, NEUTRAL);
            iconRes = a.getResourceId(R.styleable.StatusTagView_tagIcon, 0);
            a.recycle();
        }
        tagType = type;
        if (iconRes != 0) {
            icon = AppCompatResources.getDrawable(context, iconRes);
        }
        applyColors();
    }

    /** 同时设置文字与语义 */
    public void setStatus(@Nullable CharSequence text, @TagType int type) {
        setText(text);
        setTagType(type);
    }

    public void setTagType(@TagType int type) {
        if (tagType != type) {
            tagType = type;
            applyColors();
        }
    }

    @TagType
    public int getTagType() {
        return tagType;
    }

    /** 设置 16 dp 前置图标；0 表示不显示 */
    public void setTagIcon(@DrawableRes int iconRes) {
        icon = iconRes == 0 ? null : AppCompatResources.getDrawable(getContext(), iconRes);
        applyColors();
    }

    public void setTagIcon(@Nullable Drawable drawable) {
        icon = drawable;
        applyColors();
    }

    private void applyColors() {
        int[] colors = colorsOf(this, tagType);
        background.setFillColor(ColorStateList.valueOf(colors[0]));
        if (tagType == FARM) {
            background.setStroke(getResources().getDimension(R.dimen.base_stroke),
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        } else {
            background.setStroke(0f, 0);
        }
        setTextColor(colors[1]);
        Drawable start = null;
        if (icon != null) {
            start = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(start, colors[1]);
            start.setBounds(0, 0, iconSize, iconSize);
        }
        setCompoundDrawablesRelative(start, null, null, null);
    }

    /**
     * 语义对应的颜色：[0] 底色，[1] 文字与图标色。ListRowView 等控件也用它取色。
     */
    @NonNull
    static int[] colorsOf(@NonNull android.view.View view, @TagType int type) {
        int bg;
        int fg;
        switch (type) {
            case PRIMARY:
                bg = com.google.android.material.R.attr.colorPrimaryContainer;
                fg = com.google.android.material.R.attr.colorOnPrimaryContainer;
                break;
            case WARNING:
                bg = R.attr.colorWarningContainer;
                fg = R.attr.colorOnWarningContainer;
                break;
            case ERROR:
                bg = com.google.android.material.R.attr.colorErrorContainer;
                fg = com.google.android.material.R.attr.colorOnErrorContainer;
                break;
            case SUCCESS:
                bg = R.attr.colorSuccessContainer;
                fg = R.attr.colorOnSuccessContainer;
                break;
            case TERTIARY:
                bg = com.google.android.material.R.attr.colorTertiaryContainer;
                fg = com.google.android.material.R.attr.colorOnTertiaryContainer;
                break;
            case FARM:
                return new int[]{0, MaterialColors.getColor(view,
                        com.google.android.material.R.attr.colorOnSurfaceVariant)};
            case NEUTRAL:
            default:
                bg = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                fg = com.google.android.material.R.attr.colorOnSurfaceVariant;
                break;
        }
        return new int[]{MaterialColors.getColor(view, bg), MaterialColors.getColor(view, fg)};
    }
}
