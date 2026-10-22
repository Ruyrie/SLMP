package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * API-A16 修改姓名的结果。
 */
public class ProfileVO {

    @Nullable
    private String name;
    @Nullable
    private String nameInitial;
    private int version;

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getNameInitial() {
        return nameInitial;
    }

    public int getVersion() {
        return version;
    }
}
