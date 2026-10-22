package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 接受转让结果（API-F30）：成为所有者的养殖场。
 */
public class TransferAcceptVO {

    private long farmId;
    @Nullable
    private String farmName;
    @Nullable
    private String role;

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public String getRole() {
        return role;
    }
}
