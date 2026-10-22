package com.zhimu.slmp.breed.ui.common;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.databinding.BreedItemRowBinding;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

/**
 * breed_item_row 的绑定工具：重置、前置图标或天数徽标、文字、标签、尾部按钮。
 */
public final class RowBinder {

    private RowBinder() {
    }

    public static void reset(@NonNull BreedItemRowBinding b, int position, int groupSize) {
        GroupPosition.apply(b.breedRowCard, GroupPosition.of(position, groupSize));
        b.breedRowIcon.setVisibility(View.VISIBLE);
        b.breedRowBadge.setVisibility(View.GONE);
        b.breedRowLine3.setVisibility(View.GONE);
        b.breedRowExtraTag.setVisibility(View.GONE);
        b.breedRowTag.setVisibility(View.GONE);
        b.breedRowAction.setVisibility(View.GONE);
        b.breedRowAction.setOnClickListener(null);
        b.breedRowCard.setOnClickListener(null);
        b.breedRowCard.setClickable(false);
        b.breedRowCard.setFocusable(false);
    }

    public static void icon(@NonNull BreedItemRowBinding b, @DrawableRes int icon, int tone) {
        b.breedRowIcon.setVisibility(View.VISIBLE);
        b.breedRowBadge.setVisibility(View.GONE);
        b.breedRowIcon.setImageResource(icon);
        BreedUi.applyTone(b.breedRowLeading, tone, b.breedRowIcon);
    }

    /** 天数徽标：今天显示“今天”，其余为数字 + 下方“天” */
    public static void badge(@NonNull BreedItemRowBinding b, int days) {
        b.breedRowIcon.setVisibility(View.GONE);
        b.breedRowBadge.setVisibility(View.VISIBLE);
        b.breedRowBadgeNumber.setText(BreedTextFormatter.badgeNumber(days));
        b.breedRowBadgeNumber.setTextSize(days == 0 ? 14 : 16);
        b.breedRowBadgeUnit.setVisibility(days == 0 ? View.GONE : View.VISIBLE);
        BreedUi.applyTone(b.breedRowLeading, BreedUi.daysTone(days), null, b.breedRowBadgeNumber,
                b.breedRowBadgeUnit);
    }

    public static void text(@NonNull BreedItemRowBinding b, @Nullable CharSequence title,
                            @Nullable CharSequence subtitle, @Nullable CharSequence line3) {
        b.breedRowTitle.setText(title);
        b.breedRowSubtitle.setText(subtitle);
        b.breedRowSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        b.breedRowLine3.setText(line3);
        b.breedRowLine3.setVisibility(TextUtils.isEmpty(line3) ? View.GONE : View.VISIBLE);
    }

    public static void tag(@NonNull BreedItemRowBinding b, @Nullable String text, @StatusTagView.TagType int type) {
        b.breedRowTag.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        b.breedRowTag.setStatus(text, type);
    }

    public static void extraTag(@NonNull BreedItemRowBinding b, @Nullable String text,
                                @StatusTagView.TagType int type) {
        b.breedRowExtraTag.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        b.breedRowExtraTag.setStatus(text, type);
    }

    public static void action(@NonNull BreedItemRowBinding b, @Nullable CharSequence text,
                              @Nullable View.OnClickListener l) {
        boolean show = !TextUtils.isEmpty(text) && l != null;
        b.breedRowAction.setVisibility(show ? View.VISIBLE : View.GONE);
        b.breedRowAction.setText(text);
        if (show) {
            com.zhimu.slmp.base.util.ClickThrottle.setOnClickListener(b.breedRowAction, l);
        }
    }

    /** 整行可点（有涟漪）；员工只读版传 null，没有涟漪 */
    public static void click(@NonNull BreedItemRowBinding b, @Nullable View.OnClickListener l) {
        if (l == null) {
            b.breedRowCard.setOnClickListener(null);
            b.breedRowCard.setClickable(false);
            b.breedRowCard.setFocusable(false);
        } else {
            com.zhimu.slmp.base.util.ClickThrottle.setOnClickListener(b.breedRowCard, l);
        }
    }
}
