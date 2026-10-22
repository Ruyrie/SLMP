package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 转让页页头（TransferPageVO.farm；客户端暂定类名）。
 */
public class TransferFarmVO {

    private long farmId;
    @Nullable
    private String name;
    @Nullable
    private String mainCategoryName;
    @Nullable
    private String countUnit;
    private int currentStock;

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getMainCategoryName() {
        return mainCategoryName;
    }

    @Nullable
    public String getCountUnit() {
        return countUnit;
    }

    public int getCurrentStock() {
        return currentStock;
    }
}
