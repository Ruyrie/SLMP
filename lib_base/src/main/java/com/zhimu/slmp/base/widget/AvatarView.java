package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textview.MaterialTextView;
import com.zhimu.slmp.base.R;

/**
 * 姓名首字头像（02 号《UI设计规范》7.1、7.20 节）：圆形 primary-container 底 + on-primary-container 姓名首字。
 * <p>
 * 尺寸由 layout_width / layout_height 决定（顶部栏 32 dp、列表前置 40 dp），首字字号按直径自动换算
 * （32 dp 对应 14 sp）。XML：{@code app:avatarName="王建国"}；代码：{@link #setName(String)}、{@link #setInitial(String)}。
 */
public class AvatarView extends MaterialTextView {

    /** 首字字号与直径之比：32 dp 头像 14 sp */
    private static final float TEXT_RATIO = 14f / 32f;

    public AvatarView(@NonNull Context context) {
        this(context, null);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        MaterialShapeDrawable bg = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(new com.google.android.material.shape.RelativeCornerSize(0.5f))
                .build());
        bg.setFillColor(ColorStateList.valueOf(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)));
        setBackground(bg);
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer));
        setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setEllipsize(null);
        setPadding(0, 0, 0, 0);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView);
            String name = a.getString(R.styleable.AvatarView_avatarName);
            a.recycle();
            if (name != null) {
                setName(name);
            }
        }
    }

    /** 设置姓名，显示第一个字 */
    public void setName(@Nullable String name) {
        setInitial(initialOf(name));
    }

    /** 直接设置要显示的首字（例如服务端返回的 name_initial） */
    public void setInitial(@Nullable String initial) {
        setText(initial == null ? "" : initial);
        setContentDescription(initial);
    }

    /** 姓名的第一个字（按 Unicode 码点，兼容生僻字） */
    @NonNull
    public static String initialOf(@Nullable String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        return trimmed.substring(0, end);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int diameter = Math.min(w, h);
        if (diameter > 0) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, diameter * TEXT_RATIO);
        }
    }
}
