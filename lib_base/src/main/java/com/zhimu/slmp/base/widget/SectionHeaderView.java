package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;

/**
 * 分组标题（02 号《UI设计规范》2.3、2.4、7.7 节）：title-s 14 sp primary，可带计数（“今天和已逾期 · 16 项”）；
 * 同一行右侧可放文字按钮（primary 文字 + 20 dp 图标，如“日历”配 {@code calendar_month}）。
 * <p>
 * 本控件不带左右边距，放在页面 16 dp 边距的容器内；与下方列表间距 12 dp、与上一组内容间距 24 dp 由页面布局设置。
 * XML：{@code app:sectionTitle}、{@code app:sectionActionText}、{@code app:sectionActionIcon}；
 * 代码：{@link #setTitle}、{@link #setAction(CharSequence, int, OnClickListener)}。
 */
public class SectionHeaderView extends LinearLayout {

    private final TextView title;
    private final MaterialButton action;

    public SectionHeaderView(@NonNull Context context) {
        this(context, null);
    }

    public SectionHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.base_touch_target));

        title = new MaterialTextView(context);
        title.setTextAppearance(R.style.TextAppearance_Zmt_TitleSmall);
        title.setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        action = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(-getResources().getDimensionPixelSize(R.dimen.base_space_3));
        action.setVisibility(GONE);
        addView(action, lp);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SectionHeaderView);
            setTitle(a.getText(R.styleable.SectionHeaderView_sectionTitle));
            CharSequence actionText = a.getText(R.styleable.SectionHeaderView_sectionActionText);
            int actionIcon = a.getResourceId(R.styleable.SectionHeaderView_sectionActionIcon, 0);
            a.recycle();
            setActionText(actionText, actionIcon);
        }
    }

    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    @NonNull
    public TextView getTitleView() {
        return title;
    }

    /** 右侧文字按钮；text 为空时隐藏 */
    public void setAction(@Nullable CharSequence text, @DrawableRes int iconRes, @Nullable OnClickListener listener) {
        setActionText(text, iconRes);
        setOnActionClickListener(listener);
    }

    public void setOnActionClickListener(@Nullable OnClickListener listener) {
        ClickThrottle.setOnClickListener(action, listener);
    }

    @NonNull
    public MaterialButton getActionButton() {
        return action;
    }

    private void setActionText(@Nullable CharSequence text, @DrawableRes int iconRes) {
        action.setText(text);
        if (iconRes != 0) {
            action.setIconResource(iconRes);
        } else {
            action.setIcon(null);
        }
        action.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }
}
