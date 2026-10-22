package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.ui.InsetsHelper;
import com.zhimu.slmp.base.ui.SnackbarHelper;
import com.zhimu.slmp.base.util.ClickThrottle;

/**
 * 表单页底部固定操作栏（02 号《UI设计规范》2.2 节）：surface 底，上缘 1 dp outline-variant 分隔线，
 * 按钮高 40 dp、上下各留 12 dp（栏高 64 dp），左右 16 dp。
 * <ul>
 *     <li>只有主操作：整宽填充按钮（如 L08“提交”）</li>
 *     <li>有次要操作：左侧描边按钮（“取消”“忽略”）+ 右侧填充按钮，各占一半、间距 12 dp</li>
 *     <li>主按钮可带 20 dp 前置图标；危险操作用 {@code app:barPrimaryDanger="true"}（error 底）</li>
 * </ul>
 * 自动处理导航栏与软键盘 insets（键盘弹起后贴在键盘上沿），并作为 Snackbar 锚点（tag {@link SnackbarHelper#ANCHOR_TAG}）。
 * 提交中调用 {@link #setPrimaryLoading(boolean)}，按钮内显示环形进度并保持宽度。
 * XML 中写在本控件里的子视图按顺序排在按钮行中，用于特殊组合（此时不设 barPrimaryText 即可）。
 * <pre>
 * &lt;com.zhimu.slmp.base.widget.BottomActionBar
 *     android:id="@+id/action_bar"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:layout_gravity="bottom"
 *     app:barSecondaryText="@string/base_action_cancel"
 *     app:barPrimaryText="提交修改申请" /&gt;
 * </pre>
 */
public class BottomActionBar extends LinearLayout {

    private final MaterialButton secondary;
    private final MaterialButton primaryNormal;
    private final MaterialButton primaryDanger;
    private boolean danger;

    public BottomActionBar(@NonNull Context context) {
        this(context, null);
    }

    public BottomActionBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.base_bg_bottom_action_bar);
        int h = getResources().getDimensionPixelSize(R.dimen.base_space_4);
        int v = getResources().getDimensionPixelSize(R.dimen.base_space_2);
        setPadding(h, v + getResources().getDimensionPixelSize(R.dimen.base_stroke), h, v);
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.base_bottom_action_bar_height));
        LayoutInflater.from(context).inflate(R.layout.base_view_bottom_action_bar, this, true);
        secondary = findViewById(R.id.base_bar_secondary);
        primaryNormal = findViewById(R.id.base_bar_primary);
        primaryDanger = findViewById(R.id.base_bar_primary_danger);
        if (getTag() == null) {
            setTag(SnackbarHelper.ANCHOR_TAG);
        }

        CharSequence primaryText = null;
        CharSequence secondaryText = null;
        int primaryIcon = 0;
        int secondaryIcon = 0;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomActionBar);
            primaryText = a.getText(R.styleable.BottomActionBar_barPrimaryText);
            primaryIcon = a.getResourceId(R.styleable.BottomActionBar_barPrimaryIcon, 0);
            secondaryText = a.getText(R.styleable.BottomActionBar_barSecondaryText);
            secondaryIcon = a.getResourceId(R.styleable.BottomActionBar_barSecondaryIcon, 0);
            danger = a.getBoolean(R.styleable.BottomActionBar_barPrimaryDanger, false);
            a.recycle();
        }
        setPrimary(primaryText, primaryIcon);
        setSecondary(secondaryText, secondaryIcon);
        InsetsHelper.applyBottomPadding(this);
    }

    /** 主按钮（填充或危险）；text 为空时隐藏 */
    public void setPrimary(@Nullable CharSequence text, @DrawableRes int iconRes) {
        MaterialButton shown = danger ? primaryDanger : primaryNormal;
        MaterialButton hidden = danger ? primaryNormal : primaryDanger;
        hidden.setVisibility(GONE);
        shown.setText(text);
        if (iconRes != 0) {
            shown.setIconResource(iconRes);
        } else {
            shown.setIcon(null);
        }
        shown.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    public void setPrimaryText(@Nullable CharSequence text) {
        setPrimary(text, 0);
    }

    /** 次按钮（描边，在左）；text 为空时隐藏，主按钮占满整宽 */
    public void setSecondary(@Nullable CharSequence text, @DrawableRes int iconRes) {
        secondary.setText(text);
        if (iconRes != 0) {
            secondary.setIconResource(iconRes);
        } else {
            secondary.setIcon(null);
        }
        secondary.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    /** 主按钮是否为危险操作（error 底） */
    public void setPrimaryDanger(boolean danger) {
        if (this.danger == danger) {
            return;
        }
        MaterialButton old = getPrimaryButton();
        CharSequence text = old.getText();
        android.graphics.drawable.Drawable icon = old.getIcon();
        boolean enabled = old.isEnabled();
        this.danger = danger;
        setPrimary(text, 0);
        getPrimaryButton().setIcon(icon);
        getPrimaryButton().setEnabled(enabled);
    }

    public void setOnPrimaryClickListener(@Nullable OnClickListener listener) {
        ClickThrottle.setOnClickListener(primaryNormal, listener);
        ClickThrottle.setOnClickListener(primaryDanger, listener);
    }

    public void setOnSecondaryClickListener(@Nullable OnClickListener listener) {
        ClickThrottle.setOnClickListener(secondary, listener);
    }

    /** 业务条件不满足时禁用主按钮（页面顶部须用 Notice 说明原因） */
    public void setPrimaryEnabled(boolean enabled) {
        primaryNormal.setEnabled(enabled);
        primaryDanger.setEnabled(enabled);
    }

    /** 提交中：主按钮显示 20 dp 环形进度并不再响应点击 */
    public void setPrimaryLoading(boolean loading) {
        ButtonLoading.setLoading(getPrimaryButton(), loading);
    }

    @NonNull
    public MaterialButton getPrimaryButton() {
        return danger ? primaryDanger : primaryNormal;
    }

    @NonNull
    public MaterialButton getSecondaryButton() {
        return secondary;
    }
}
