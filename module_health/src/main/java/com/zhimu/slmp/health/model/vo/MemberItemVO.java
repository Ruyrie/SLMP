package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-F16 成员列表项（复用模块02，执行人下拉只用 user_id 与 name）。
 */
public class MemberItemVO {

    private long userId;
    @Nullable
    private String name;
    @Nullable
    private String role;
    /** 是否为当前用户（F16 is_me） */
    private boolean isMe;

    public long getUserId() {
        return userId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    public boolean isMe() {
        return isMe;
    }
}
