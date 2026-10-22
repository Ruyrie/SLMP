package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.base.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 分组列表项（02 号《UI设计规范》7.7 节）：前置区 → 正文 → 尾部。
 * <ul>
 *     <li>前置区：40 × 40 dp 图标块（12 dp 圆角，底色按语义 {@link #setLeadingIcon(int, int)}），或姓名首字头像 {@link #setAvatarName(String)}</li>
 *     <li>正文：主文字 title-m 16 sp（单行省略）、辅助文字 body-m 14 sp（最多 2 行）；
 *     XML 中写在本控件里的子视图放到辅助文字下方的扩展区（修改前后对比、行内按钮等）</li>
 *     <li>尾部：状态标签 {@link #setStatusTag(CharSequence, int, int)}、数值或说明文字、图标、{@code chevron_right}</li>
 * </ul>
 * 底色 surface-container-lowest，无描边无阴影；左右内边距 16 dp、上下 12 dp，单行最小高 56 dp、双行 72 dp。
 * 按位置设置圆角：{@code app:groupPosition="top|middle|bottom|single"} 或 {@link #setGroupPosition(int)}；
 * 放在 {@link GroupedListLayout} 中时自动设置。设置点击监听后整项显示水波纹（形状跟随圆角），只展示的项不设监听。
 * <pre>
 * &lt;com.zhimu.slmp.base.widget.ListRowView
 *     app:rowLeadingIcon="@drawable/ic_rice_bowl"
 *     app:leadingTone="error"
 *     app:rowTitle="1 号保育舍 · 早餐饲喂"
 *     app:rowSubtitle="投喂时间 07:00 · 张伟"
 *     app:rowTagText="已逾期"
 *     app:tagType="error"
 *     app:rowTagIcon="@drawable/ic_schedule" /&gt;
 * </pre>
 */
public class ListRowView extends MaterialCardView {

    /** 前置图标块语义（02 号 3.3 节） */
    public static final int TONE_SECONDARY = 0;
    public static final int TONE_ERROR = 1;
    public static final int TONE_WARNING = 2;
    public static final int TONE_TERTIARY = 3;
    public static final int TONE_NEUTRAL = 4;
    public static final int TONE_PRIMARY = 5;

    @IntDef({TONE_SECONDARY, TONE_ERROR, TONE_WARNING, TONE_TERTIARY, TONE_NEUTRAL, TONE_PRIMARY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Tone {
    }

    private final LinearLayout root;
    private final View leading;
    private final ImageView leadingIcon;
    private final AvatarView avatar;
    private final ImageView unreadDot;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout extra;
    private final StatusTagView tag;
    private final TextView trailingText;
    private final ImageView trailingIcon;
    private final ImageView chevron;
    private final MaterialShapeDrawable leadingBackground;
    private final int minHeightSingle;
    private final int minHeightTwoLine;

    private int groupPosition = -1;
    private int leadingTone = TONE_SECONDARY;
    private boolean inflated;

    public ListRowView(@NonNull Context context) {
        this(context, null);
    }

    public ListRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public ListRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.base_view_list_row, this, true);
        inflated = true;
        root = findViewById(R.id.base_row_root);
        leading = findViewById(R.id.base_row_leading);
        leadingIcon = findViewById(R.id.base_row_leading_icon);
        avatar = findViewById(R.id.base_row_avatar);
        unreadDot = findViewById(R.id.base_row_unread);
        title = findViewById(R.id.base_row_title);
        subtitle = findViewById(R.id.base_row_subtitle);
        extra = findViewById(R.id.base_row_extra);
        tag = findViewById(R.id.base_row_tag);
        trailingText = findViewById(R.id.base_row_trailing_text);
        trailingIcon = findViewById(R.id.base_row_trailing_icon);
        chevron = findViewById(R.id.base_row_chevron);
        minHeightSingle = getResources().getDimensionPixelSize(R.dimen.base_list_item_min_height);
        minHeightTwoLine = getResources().getDimensionPixelSize(R.dimen.base_list_item_two_line_min_height);

        setCardBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerLowest));
        setCardElevation(0f);
        setStrokeWidth(0);

        leadingBackground = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(getResources().getDimension(R.dimen.base_radius_md))
                .build());
        leadingIcon.setBackground(leadingBackground);

        int position = GroupPosition.SINGLE;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ListRowView);
            position = a.getInt(R.styleable.ListRowView_groupPosition, GroupPosition.SINGLE);
            leadingTone = a.getInt(R.styleable.ListRowView_leadingTone, TONE_SECONDARY);
            int iconRes = a.getResourceId(R.styleable.ListRowView_rowLeadingIcon, 0);
            String avatarName = a.getString(R.styleable.ListRowView_rowAvatarName);
            setTitle(a.getText(R.styleable.ListRowView_rowTitle));
            setSubtitle(a.getText(R.styleable.ListRowView_rowSubtitle));
            subtitle.setMaxLines(a.getInt(R.styleable.ListRowView_rowSubtitleMaxLines, 2));
            setTrailingText(a.getText(R.styleable.ListRowView_rowTrailingText));
            int trailingIconRes = a.getResourceId(R.styleable.ListRowView_rowTrailingIcon, 0);
            CharSequence tagText = a.getText(R.styleable.ListRowView_rowTagText);
            int tagType = a.getInt(R.styleable.ListRowView_tagType, StatusTagView.NEUTRAL);
            int tagIcon = a.getResourceId(R.styleable.ListRowView_rowTagIcon, 0);
            setShowChevron(a.getBoolean(R.styleable.ListRowView_rowShowChevron, false));
            setUnread(a.getBoolean(R.styleable.ListRowView_rowUnread, false));
            setTitleStrikeThrough(a.getBoolean(R.styleable.ListRowView_rowTitleStrikeThrough, false));
            setDanger(a.getBoolean(R.styleable.ListRowView_rowDanger, false));
            a.recycle();
            if (iconRes != 0) {
                setLeadingIcon(iconRes, leadingTone);
            } else if (avatarName != null) {
                setAvatarName(avatarName);
            }
            setTrailingIcon(trailingIconRes);
            setStatusTag(tagText, tagType, tagIcon);
        }
        setGroupPosition(position);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // XML 中写在 ListRowView 内的子视图移到扩展区
        while (getChildCount() > 1) {
            View child = getChildAt(1);
            removeViewAt(1);
            extra.addView(child);
        }
        extra.setVisibility(extra.getChildCount() > 0 ? VISIBLE : GONE);
    }

    // ---------------- 位置与圆角 ----------------

    public void setGroupPosition(@GroupPosition.Value int position) {
        if (groupPosition != position) {
            groupPosition = position;
            GroupPosition.apply(this, position);
        }
    }

    public int getGroupPosition() {
        return groupPosition;
    }

    // ---------------- 前置区 ----------------

    /** 前置 40 dp 图标块，tone 为 {@link #TONE_SECONDARY} 等语义；iconRes 为 0 时隐藏前置区 */
    public void setLeadingIcon(@DrawableRes int iconRes, @Tone int tone) {
        setLeadingIcon(iconRes == 0 ? null : AppCompatResources.getDrawable(getContext(), iconRes), tone);
    }

    public void setLeadingIcon(@Nullable Drawable icon, @Tone int tone) {
        leadingTone = tone;
        avatar.setVisibility(GONE);
        if (icon == null) {
            leadingIcon.setVisibility(GONE);
            leading.setVisibility(GONE);
            return;
        }
        leadingIcon.setImageDrawable(icon);
        applyLeadingTone();
        leadingIcon.setVisibility(VISIBLE);
        leading.setVisibility(VISIBLE);
    }

    public void setLeadingTone(@Tone int tone) {
        leadingTone = tone;
        applyLeadingTone();
    }

    /** 前置姓名首字头像（40 dp，primary-container 底） */
    public void setAvatarName(@Nullable String name) {
        leadingIcon.setVisibility(GONE);
        if (TextUtils.isEmpty(name)) {
            avatar.setVisibility(GONE);
            leading.setVisibility(GONE);
            return;
        }
        avatar.setName(name);
        avatar.setVisibility(VISIBLE);
        leading.setVisibility(VISIBLE);
    }

    private void applyLeadingTone() {
        int bgAttr;
        int fgAttr;
        switch (leadingTone) {
            case TONE_ERROR:
                bgAttr = com.google.android.material.R.attr.colorErrorContainer;
                fgAttr = com.google.android.material.R.attr.colorOnErrorContainer;
                break;
            case TONE_WARNING:
                bgAttr = R.attr.colorWarningContainer;
                fgAttr = R.attr.colorOnWarningContainer;
                break;
            case TONE_TERTIARY:
                bgAttr = com.google.android.material.R.attr.colorTertiaryContainer;
                fgAttr = com.google.android.material.R.attr.colorOnTertiaryContainer;
                break;
            case TONE_NEUTRAL:
                bgAttr = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                fgAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
                break;
            case TONE_PRIMARY:
                bgAttr = com.google.android.material.R.attr.colorPrimaryContainer;
                fgAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;
                break;
            case TONE_SECONDARY:
            default:
                bgAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                fgAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
                break;
        }
        leadingBackground.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(this, bgAttr)));
        leadingIcon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(this, fgAttr)));
    }

    // ---------------- 正文 ----------------

    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    @NonNull
    public CharSequence getTitle() {
        return title.getText();
    }

    /** 辅助文字（最多 2 行）；为空时隐藏 */
    public void setSubtitle(@Nullable CharSequence text) {
        subtitle.setText(text);
        subtitle.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        root.setMinimumHeight(TextUtils.isEmpty(text) ? minHeightSingle : minHeightTwoLine);
    }

    public void setSubtitleMaxLines(int maxLines) {
        subtitle.setMaxLines(maxLines);
    }

    /** 已完成的待办：主文字加删除线 */
    public void setTitleStrikeThrough(boolean strike) {
        int flags = title.getPaintFlags();
        title.setPaintFlags(strike ? flags | Paint.STRIKE_THRU_TEXT_FLAG : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    /** 未读消息：主文字左侧 8 dp primary 圆点 */
    /** 危险操作项：标题 error 色（02 号规范：停用、移出、退出养殖场等用列表项样式，不用填充色按钮） */
    public void setDanger(boolean danger) {
        title.setTextColor(MaterialColors.getColor(this, danger ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurface));
    }

    public void setUnread(boolean unread) {
        unreadDot.setVisibility(unread ? VISIBLE : GONE);
    }

    /** 辅助文字下方的扩展区，可加入自定义视图 */
    @NonNull
    public LinearLayout getExtraContainer() {
        extra.setVisibility(VISIBLE);
        return extra;
    }

    // ---------------- 尾部 ----------------

    /**
     * 尾部状态标签（一个列表项最多一个）。
     *
     * @param type    {@link StatusTagView#NEUTRAL} 等语义
     * @param iconRes 16 dp 图标，0 表示无
     */
    public void setStatusTag(@Nullable CharSequence text, @StatusTagView.TagType int type, @DrawableRes int iconRes) {
        if (TextUtils.isEmpty(text)) {
            tag.setVisibility(GONE);
            return;
        }
        tag.setStatus(text, type);
        tag.setTagIcon(iconRes);
        tag.setVisibility(VISIBLE);
    }

    @NonNull
    public StatusTagView getStatusTagView() {
        return tag;
    }

    /** 尾部数值或说明（右对齐，等宽数字）；为空时隐藏 */
    public void setTrailingText(@Nullable CharSequence text) {
        trailingText.setText(text);
        trailingText.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    /** 尾部 24 dp 图标（如 {@code more_vert}、{@code edit}）；0 表示隐藏 */
    public void setTrailingIcon(@DrawableRes int iconRes) {
        if (iconRes == 0) {
            trailingIcon.setVisibility(GONE);
        } else {
            trailingIcon.setImageResource(iconRes);
            trailingIcon.setVisibility(VISIBLE);
        }
    }

    @NonNull
    public ImageView getTrailingIconView() {
        return trailingIcon;
    }

    /** 尾部 {@code chevron_right}（进入下一级） */
    public void setShowChevron(boolean show) {
        chevron.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * 不可选的选项：文字改为 on-surface-variant 并屏蔽点击（7.7 节“不可选的选项”）。
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!inflated) {
            return;
        }
        title.setTextColor(MaterialColors.getColor(this, enabled
                ? com.google.android.material.R.attr.colorOnSurface
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        setClickable(l != null);
        setFocusable(l != null);
    }
}
