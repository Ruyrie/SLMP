package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 超容量确认（42206 的 data）。
 */
public class CapacityConfirmVO {

    private boolean needConfirm;
    private long penId;
    @Nullable
    private String penName;
    @Nullable
    private String unit;
    private int capacity;
    private int currentCount;
    private int afterCount;
    private int overCount;

    public boolean isNeedConfirm() {
        return needConfirm;
    }

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getAfterCount() {
        return afterCount;
    }

    public int getOverCount() {
        return overCount;
    }

    /** 提交前按栏舍容量本地预判 */
    public static CapacityConfirmVO local(long penId, @Nullable String penName, @Nullable String unit, int capacity,
                                          int currentCount, int adding) {
        CapacityConfirmVO c = new CapacityConfirmVO();
        c.needConfirm = true;
        c.penId = penId;
        c.penName = penName;
        c.unit = unit;
        c.capacity = capacity;
        c.currentCount = currentCount;
        c.afterCount = currentCount + adding;
        c.overCount = c.afterCount - capacity;
        return c;
    }
}
