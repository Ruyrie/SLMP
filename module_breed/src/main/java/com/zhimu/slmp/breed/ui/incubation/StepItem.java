package com.zhimu.slmp.breed.ui.incubation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * B07 步骤条的一个节点：入孵 / 照蛋 / 出雏，状态为已完成、当前、未到达，下方两行说明。
 */
public final class StepItem {

    public static final int DONE = 0;
    public static final int CURRENT = 1;
    public static final int PENDING = 2;

    private final int step;
    @NonNull
    private final String name;
    private final int state;
    @Nullable
    private final String line1;
    @Nullable
    private final String line2;

    public StepItem(int step, @NonNull String name, int state, @Nullable String line1, @Nullable String line2) {
        this.step = step;
        this.name = name;
        this.state = state;
        this.line1 = line1;
        this.line2 = line2;
    }

    /** 1 入孵、2 照蛋、3 出雏 */
    public int getStep() {
        return step;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public int getState() {
        return state;
    }

    @Nullable
    public String getLine1() {
        return line1;
    }

    @Nullable
    public String getLine2() {
        return line2;
    }
}
