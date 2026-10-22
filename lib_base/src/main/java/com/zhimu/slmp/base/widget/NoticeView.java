package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 页内横幅 Notice（02 号《UI设计规范》7.10 节）：必须让用户看到的规则、风险或流程说明，常驻页面、不能关闭；
 * 不用它告知操作结果（用 Snackbar）。
 * <p>
 * 结构：左侧 20 dp 图标 + 正文（加粗的结论句 + 原因或下一步）+ 右侧最多一个文字按钮（与正文同色），
 * 或正文下方一个确认复选框（如“我已确认，仍要入栏”）。宽同内容区，圆角 16 dp，内边距 16 dp，正文 body-m 14 sp。
 * <ul>
 *     <li>{@link #LEVEL_NORMAL} 默认级：secondary-container，默认图标 {@code info}（流程说明）</li>
 *     <li>{@link #LEVEL_WARNING}：warning-container，默认图标 {@code warning}（休药期拦截、库存不足、超出容量）</li>
 *     <li>{@link #LEVEL_ERROR}：error-container，默认图标 {@code block}（疑似重大动物疫病、无权操作）</li>
 *     <li>{@link #LEVEL_SUCCESS}：success-container，默认图标 {@code check_circle}（本规范补充）</li>
 * </ul>
 * XML：{@code app:noticeLevel="warning" app:noticeTitle="休药中，剩 10 天。" app:noticeText="可出栏日期 10月29日。"}；
 * 代码：{@link #setNotice(CharSequence, CharSequence)}、{@link #setAction(CharSequence, OnClickListener)}、
 * {@link #setCheckText(CharSequence)}、{@link #isChecked()}。
 */
public class NoticeView extends LinearLayout {

    public static final int LEVEL_NORMAL = 0;
    public static final int LEVEL_WARNING = 1;
    public static final int LEVEL_ERROR = 2;
    public static final int LEVEL_SUCCESS = 3;

    @IntDef({LEVEL_NORMAL, LEVEL_WARNING, LEVEL_ERROR, LEVEL_SUCCESS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Level {
    }

    private final ImageView icon;
    private final TextView text;
    private final MaterialButton action;
    private final MaterialCheckBox check;
    private final MaterialShapeDrawable background;

    private int level = LEVEL_NORMAL;
    @DrawableRes
    private int iconRes;
    @Nullable
    private CharSequence title;
    @Nullable
    private CharSequence body;

    public NoticeView(@NonNull Context context) {
        this(context, null);
    }

    public NoticeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.base_space_4);
        setPadding(padding, padding, padding, padding);
        LayoutInflater.from(context).inflate(R.layout.base_view_notice, this, true);
        icon = findViewById(R.id.base_notice_icon);
        text = findViewById(R.id.base_notice_text);
        action = findViewById(R.id.base_notice_action);
        check = findViewById(R.id.base_notice_check);
        background = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(getResources().getDimension(R.dimen.base_radius_lg))
                .build());
        setBackground(background);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NoticeView);
            level = a.getInt(R.styleable.NoticeView_noticeLevel, LEVEL_NORMAL);
            iconRes = a.getResourceId(R.styleable.NoticeView_noticeIcon, 0);
            title = a.getText(R.styleable.NoticeView_noticeTitle);
            body = a.getText(R.styleable.NoticeView_noticeText);
            CharSequence actionText = a.getText(R.styleable.NoticeView_noticeActionText);
            CharSequence checkText = a.getText(R.styleable.NoticeView_noticeCheckText);
            a.recycle();
            setActionText(actionText);
            setCheckText(checkText);
        }
        applyLevel();
        renderText();
    }

    /** 设置级别：{@link #LEVEL_NORMAL}、{@link #LEVEL_WARNING}、{@link #LEVEL_ERROR}、{@link #LEVEL_SUCCESS} */
    public void setLevel(@Level int level) {
        this.level = level;
        applyLevel();
    }

    @Level
    public int getLevel() {
        return level;
    }

    /** 自定义图标（如 {@code qr_code_2}、{@code inventory_2}、{@code medication}）；0 为该级别的默认图标 */
    public void setIcon(@DrawableRes int iconRes) {
        this.iconRes = iconRes;
        applyLevel();
    }

    /**
     * 设置正文。
     *
     * @param title 结论句，加粗显示在最前（可为空）
     * @param text  原因或下一步
     */
    public void setNotice(@Nullable CharSequence title, @Nullable CharSequence text) {
        this.title = title;
        this.body = text;
        renderText();
    }

    public void setText(@Nullable CharSequence text) {
        setNotice(null, text);
    }

    /** 右侧文字按钮（如“去入库”“去异常上报”），text 为空时隐藏 */
    public void setAction(@Nullable CharSequence text, @Nullable OnClickListener listener) {
        setActionText(text);
        ClickThrottle.setOnClickListener(action, listener);
    }

    /** 正文下方的确认复选框文字，为空时隐藏 */
    public void setCheckText(@Nullable CharSequence checkText) {
        check.setText(checkText);
        check.setVisibility(TextUtils.isEmpty(checkText) ? GONE : VISIBLE);
    }

    public boolean isChecked() {
        return check.isChecked();
    }

    public void setChecked(boolean checked) {
        check.setChecked(checked);
    }

    public void setOnCheckedChangeListener(@Nullable CompoundButton.OnCheckedChangeListener listener) {
        check.setOnCheckedChangeListener(listener);
    }

    @NonNull
    public MaterialCheckBox getCheckBox() {
        return check;
    }

    private void setActionText(@Nullable CharSequence actionText) {
        action.setText(actionText);
        action.setVisibility(TextUtils.isEmpty(actionText) ? GONE : VISIBLE);
    }

    private void renderText() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(title)) {
            sb.append(title);
            // 结论句用 Medium 500（4.1 节只用 400 / 500 两种字重）
            sb.setSpan(new TypefaceSpan(Typeface.create(Typeface.DEFAULT, 500, false)),
                    0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!TextUtils.isEmpty(body)) {
            sb.append(body);
        }
        text.setText(sb);
    }

    private void applyLevel() {
        int bgAttr;
        int fgAttr;
        int defaultIcon;
        switch (level) {
            case LEVEL_WARNING:
                bgAttr = R.attr.colorWarningContainer;
                fgAttr = R.attr.colorOnWarningContainer;
                defaultIcon = R.drawable.ic_warning;
                break;
            case LEVEL_ERROR:
                bgAttr = com.google.android.material.R.attr.colorErrorContainer;
                fgAttr = com.google.android.material.R.attr.colorOnErrorContainer;
                defaultIcon = R.drawable.ic_block;
                break;
            case LEVEL_SUCCESS:
                bgAttr = R.attr.colorSuccessContainer;
                fgAttr = R.attr.colorOnSuccessContainer;
                defaultIcon = R.drawable.ic_check_circle;
                break;
            case LEVEL_NORMAL:
            default:
                bgAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                fgAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
                defaultIcon = R.drawable.ic_info;
                break;
        }
        int bg = MaterialColors.getColor(this, bgAttr);
        int fg = MaterialColors.getColor(this, fgAttr);
        background.setFillColor(ColorStateList.valueOf(bg));
        icon.setImageResource(iconRes != 0 ? iconRes : defaultIcon);
        icon.setImageTintList(ColorStateList.valueOf(fg));
        text.setTextColor(fg);
        // 横幅中的操作文字与正文同色
        action.setTextColor(fg);
        action.setIconTint(ColorStateList.valueOf(fg));
        action.setRippleColor(ColorStateList.valueOf(MaterialColors.compositeARGBWithAlpha(fg, 26)));
        // 复选框：实心底用对应 on-*-container，对勾用 *-container（7.20 节）
        check.setTextColor(fg);
        check.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{fg, fg}));
        check.setButtonIconTintList(ColorStateList.valueOf(bg));
    }
}
