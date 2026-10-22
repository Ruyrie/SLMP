package com.zhimu.slmp.health.ui.common;

import android.content.res.ColorStateList;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * 列表项 40 × 40 dp、圆角 12 的前置图标底按状态取色（UI 文档 2.2、2.3 节）：
 * 待接种 / 待执行 / 启用中 / 用药记录为 secondary-container；漏免 / 已逾期为 error-container；
 * 已延期 / 最晚为 warning-container；已接种 / 已执行 / 已暂停 / 已取消为 surface-container-highest。
 */
public final class ToneStyle {

    public static final int SECONDARY = 0;
    public static final int ERROR = 1;
    public static final int WARNING = 2;
    public static final int NEUTRAL = 3;
    public static final int SUCCESS = 4;

    private ToneStyle() {
    }

    public static void apply(@NonNull ImageView icon, int tone) {
        int bgAttr;
        int fgAttr;
        switch (tone) {
            case ERROR:
                bgAttr = com.google.android.material.R.attr.colorErrorContainer;
                fgAttr = com.google.android.material.R.attr.colorOnErrorContainer;
                break;
            case WARNING:
                bgAttr = com.zhimu.slmp.base.R.attr.colorWarningContainer;
                fgAttr = com.zhimu.slmp.base.R.attr.colorOnWarningContainer;
                break;
            case SUCCESS:
                bgAttr = com.zhimu.slmp.base.R.attr.colorSuccessContainer;
                fgAttr = com.zhimu.slmp.base.R.attr.colorOnSuccessContainer;
                break;
            case NEUTRAL:
                bgAttr = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                fgAttr = com.google.android.material.R.attr.colorOnSurfaceVariant;
                break;
            case SECONDARY:
            default:
                bgAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                fgAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
                break;
        }
        MaterialShapeDrawable bg = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(icon.getResources().getDimension(com.zhimu.slmp.base.R.dimen.base_radius_md))
                .build());
        bg.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(icon, bgAttr)));
        icon.setBackground(bg);
        icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(icon, fgAttr)));
    }
}
