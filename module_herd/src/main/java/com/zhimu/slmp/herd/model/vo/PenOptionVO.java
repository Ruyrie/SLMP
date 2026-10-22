package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * API-L07（usage = OPTIONS）栏舍下拉项：只含启用栏舍；传 category_id 时其他品类在栏的栏舍 disabled = true。
 */
public class PenOptionVO {

    private long penId;
    private String penCode;
    private String penName;
    private String penType;
    private int stockCount;
    private int capacity;
    private boolean disabled;
    private String hint;
    private String unitText;

    public long getPenId() { return penId; }

    public String getPenCode() { return penCode; }

    public String getPenName() { return penName; }

    public String getPenType() { return penType; }

    public int getStockCount() { return stockCount; }

    public int getCapacity() { return capacity; }

    public boolean isDisabled() { return disabled; }

    @Nullable
    public String getHint() { return hint; }

    @Nullable
    public String getUnitText() { return unitText; }
}
