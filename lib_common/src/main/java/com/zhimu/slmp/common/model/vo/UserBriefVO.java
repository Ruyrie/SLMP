package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 记录中的登记人、审核人、成员。
 */
public class UserBriefVO {

    private long userId;
    @Nullable
    private String name;
    @Nullable
    private String avatarUrl;
    /** OWNER / MANAGER / STAFF */
    @Nullable
    private String role;
    @Nullable
    private String roleText;

    public long getUserId() {
        return userId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getAvatarUrl() {
        return avatarUrl;
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
