package com.zhimu.slmp.feed.ui.growth;

import androidx.annotation.NonNull;

import java.util.Objects;

/** G06 图例一项：实际（实线）、标准（虚线）、对比批次（点线）、允许偏差（色块）。 */
public final class GrowthLegendItem {

    public static final int ACTUAL = 0;
    public static final int STD = 1;
    public static final int COMPARE = 2;
    public static final int BAND = 3;

    public final int kind;
    @NonNull
    public final String text;

    public GrowthLegendItem(int kind, @NonNull String text) {
        this.kind = kind;
        this.text = text;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GrowthLegendItem)) {
            return false;
        }
        GrowthLegendItem g = (GrowthLegendItem) o;
        return kind == g.kind && text.equals(g.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text);
    }
}
