package com.zhimu.slmp.breed.ui.common;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.breed.model.BreedCodes;

/**
 * 本模块的状态配色（UI 2.2 节“前置图标底的颜色规则”“母猪状态标签”）。
 */
public final class BreedUi {

    /** 3 天内预产（含今天）：warning-container */
    public static final int TONE_WARNING = 0;
    /** 繁殖周期中、4–7 天内：secondary-container */
    public static final int TONE_SECONDARY = 1;
    /** 空怀、更晚、历史记录：surface-container-highest */
    public static final int TONE_NEUTRAL = 2;

    private BreedUi() {
    }

    /** 母猪状态标签：妊娠、哺乳 primary-container；已配种、空怀 surface-container-highest；已淘汰、死亡 error-container */
    @StatusTagView.TagType
    public static int statusTagType(@Nullable String status) {
        if (BreedCodes.PREGNANT.equals(status) || BreedCodes.LACTATING.equals(status)) {
            return StatusTagView.PRIMARY;
        }
        if (BreedCodes.isExited(status)) {
            return StatusTagView.ERROR;
        }
        return StatusTagView.NEUTRAL;
    }

    /** 母猪前置图标底：临产 warning；已配种、妊娠、哺乳 secondary；空怀、终态 neutral */
    public static int animalTone(@Nullable String status, @Nullable Integer daysToDue) {
        if (BreedCodes.PREGNANT.equals(status) && daysToDue != null && daysToDue <= 3) {
            return TONE_WARNING;
        }
        if (BreedCodes.MATED.equals(status) || BreedCodes.PREGNANT.equals(status)
                || BreedCodes.LACTATING.equals(status)) {
            return TONE_SECONDARY;
        }
        return TONE_NEUTRAL;
    }

    /** 天数徽标：0–3 天（含超期）warning，4–7 天 secondary，8 天及以上 neutral */
    public static int daysTone(int days) {
        if (days <= 3) {
            return TONE_WARNING;
        }
        return days <= 7 ? TONE_SECONDARY : TONE_NEUTRAL;
    }

    /** 给 40 dp 图标底着色，前景（图标、徽标文字）取对应的 on-* 颜色 */
    public static void applyTone(@NonNull View box, int tone, @Nullable ImageView icon, @Nullable TextView... texts) {
        int bg = MaterialColors.getColor(box, containerAttr(tone));
        int fg = MaterialColors.getColor(box, onContainerAttr(tone));
        box.setBackgroundTintList(ColorStateList.valueOf(bg));
        if (icon != null) {
            icon.setImageTintList(ColorStateList.valueOf(fg));
        }
        if (texts != null) {
            for (TextView t : texts) {
                if (t != null) {
                    t.setTextColor(fg);
                }
            }
        }
    }

    @AttrRes
    private static int containerAttr(int tone) {
        if (tone == TONE_WARNING) {
            return com.zhimu.slmp.base.R.attr.colorWarningContainer;
        }
        if (tone == TONE_SECONDARY) {
            return com.google.android.material.R.attr.colorSecondaryContainer;
        }
        return com.google.android.material.R.attr.colorSurfaceContainerHighest;
    }

    @AttrRes
    private static int onContainerAttr(int tone) {
        if (tone == TONE_WARNING) {
            return com.zhimu.slmp.base.R.attr.colorOnWarningContainer;
        }
        if (tone == TONE_SECONDARY) {
            return com.google.android.material.R.attr.colorOnSecondaryContainer;
        }
        return com.google.android.material.R.attr.colorOnSurfaceVariant;
    }
}
