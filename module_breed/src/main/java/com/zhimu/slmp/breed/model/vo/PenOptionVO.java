package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 栏舍下拉（API-L07 usage = OPTIONS）：B04 改选入栏栏舍、B07 入栏鸡舍。
 */
public class PenOptionVO {

    private long penId;
    @Nullable
    private String penCode;
    @Nullable
    private String penName;
    @Nullable
    private String penType;
    private int stockCount;
    private int capacity;
    private boolean disabled;
    @Nullable
    private String hint;

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenCode() {
        return penCode;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getPenType() {
        return penType;
    }

    public int getStockCount() {
        return stockCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isDisabled() {
        return disabled;
    }

    @Nullable
    public String getHint() {
        return hint;
    }
}
