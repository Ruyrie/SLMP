package com.zhimu.slmp.base.widget;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.base.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 分组列表项的位置与圆角（02 号《UI设计规范》2.4、5.2 节）。
 * <ul>
 *     <li>首项（TOP）：上方两角 20 dp，下方两角 4 dp</li>
 *     <li>中间项（MIDDLE）：四角 4 dp</li>
 *     <li>末项（BOTTOM）：上方两角 4 dp，下方两角 20 dp</li>
 *     <li>只有一项（SINGLE）：四角 20 dp</li>
 * </ul>
 * RecyclerView 的 Adapter 中用 {@link #of(int, int)} 算出位置，再用 {@link #apply(MaterialCardView, int)} 设置卡片形状；
 * 项间 2 dp 缝隙用 {@link GroupedListDecoration}。静态布局可直接放进 {@link GroupedListLayout} 自动处理。
 */
public final class GroupPosition {

    public static final int SINGLE = 0;
    public static final int TOP = 1;
    public static final int MIDDLE = 2;
    public static final int BOTTOM = 3;

    @IntDef({SINGLE, TOP, MIDDLE, BOTTOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Value {
    }

    private GroupPosition() {
    }

    /** 按组内下标与组内数量算出位置 */
    @Value
    public static int of(int indexInGroup, int groupSize) {
        if (groupSize <= 1) {
            return SINGLE;
        }
        if (indexInGroup <= 0) {
            return TOP;
        }
        if (indexInGroup >= groupSize - 1) {
            return BOTTOM;
        }
        return MIDDLE;
    }

    /** 该位置对应的形状 */
    @NonNull
    public static ShapeAppearanceModel shape(@NonNull Context context, @Value int position) {
        Resources res = context.getResources();
        float outer = res.getDimension(R.dimen.base_radius_lg_plus);
        float inner = res.getDimension(R.dimen.base_radius_xs);
        boolean roundTop = position == SINGLE || position == TOP;
        boolean roundBottom = position == SINGLE || position == BOTTOM;
        return ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, roundTop ? outer : inner)
                .setTopRightCorner(CornerFamily.ROUNDED, roundTop ? outer : inner)
                .setBottomLeftCorner(CornerFamily.ROUNDED, roundBottom ? outer : inner)
                .setBottomRightCorner(CornerFamily.ROUNDED, roundBottom ? outer : inner)
                .build();
    }

    /** 把卡片设置为该位置的形状（水波纹随形状裁剪） */
    public static void apply(@NonNull MaterialCardView card, @Value int position) {
        card.setShapeAppearanceModel(shape(card.getContext(), position));
    }
}
