package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 同意邀请结果（API-F21）：加入的养殖场与角色。
 */
public class InviteAcceptVO {

    private long farmId;
    @Nullable
    private String farmName;
    @Nullable
    private String role;
    @Nullable
    private String roleText;

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

    @Nullable
    public String getRoleText() {
        return roleText;
    }
}
