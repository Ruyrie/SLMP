package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * 注销后将要退出的养殖场（本人为场长或员工）。
 */
public class CancelExitFarmVO {

    private long farmId;
    @Nullable
    private String name;
    @Nullable
    private String role;
    @Nullable
    private String roleText;

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }
}
