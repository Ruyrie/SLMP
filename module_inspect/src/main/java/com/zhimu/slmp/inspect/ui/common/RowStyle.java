package com.zhimu.slmp.inspect.ui.common;

import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.inspect.R;

/**
 * 列表项外观：前置图标块语义（异常 error-container、待巡 secondary-container、已巡 surface-container-highest、
 * AI tertiary-container）、分组圆角（首末 20、中间 4）、尾部状态标签。
 */
public final class RowStyle {

    public static final int TONE_SECONDARY = 0;
    public static final int TONE_ERROR = 1;
    public static final int TONE_NEUTRAL = 2;
    public static final int TONE_TERTIARY = 3;

    private RowStyle() {
    }

    public static void leading(@NonNull ImageView v, @DrawableRes int icon, int tone) {
        int bg;
        int fg;
        switch (tone) {
            case TONE_ERROR:
                bg = R.drawable.inspect_bg_leading_error;
                fg = com.zhimu.slmp.base.R.color.zm_on_error_container;
                break;
            case TONE_NEUTRAL:
                bg = R.drawable.inspect_bg_leading_neutral;
                fg = com.zhimu.slmp.base.R.color.zm_on_surface_variant;
                break;
            case TONE_TERTIARY:
                bg = R.drawable.inspect_bg_leading_tertiary;
                fg = com.zhimu.slmp.base.R.color.zm_on_tertiary_container;
                break;
            default:
                bg = R.drawable.inspect_bg_leading_secondary;
                fg = com.zhimu.slmp.base.R.color.zm_on_secondary_container;
                break;
        }
        v.setBackgroundResource(bg);
        v.setImageResource(icon);
        v.setImageTintList(ContextCompat.getColorStateList(v.getContext(), fg));
    }

    /** 分组圆角：index 为组内位置 */
    public static void position(@NonNull MaterialCardView card, int index, int size) {
        GroupPosition.apply(card, GroupPosition.of(index, size));
    }

    /** 尾部标签；text 为空时隐藏 */
    public static void tag(@NonNull StatusTagView tag, @Nullable CharSequence text,
                           @StatusTagView.TagType int type, @DrawableRes int icon) {
        if (text == null || text.length() == 0) {
            tag.setVisibility(android.view.View.GONE);
            return;
        }
        tag.setStatus(text, type);
        tag.setTagIcon(icon);
        tag.setVisibility(android.view.View.VISIBLE);
    }
}
