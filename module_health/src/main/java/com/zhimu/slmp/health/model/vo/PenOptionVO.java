package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-L07（usage = OPTIONS）本场启用中栏舍（复用模块04，待定事项 2；字段见模块04 文档 PenOptionVO）。
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
