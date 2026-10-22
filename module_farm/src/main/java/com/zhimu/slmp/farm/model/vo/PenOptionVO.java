package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 可分配栏舍（API-F18）：selected 为当前成员已负责；other_staff_count 为 0 表示取消后无人负责。
 */
public class PenOptionVO {

    private long penId;
    @Nullable
    private String penCode;
    @Nullable
    private String name;
    private boolean selected;
    private int otherStaffCount;

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenCode() {
        return penCode;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public boolean isSelected() {
        return selected;
    }

    public int getOtherStaffCount() {
        return otherStaffCount;
    }
}
