package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 记录中的登记人、审核人、成员。栏舍详情的负责员工另带 member_id（t_farm_member 的 id，用于打开 F09 成员详情）。
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
    /** 成员编号（养殖场成员表 id）；只有栏舍负责员工等需要打开成员详情的场景返回 */
    @Nullable
    private Long memberId;

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

    @Nullable
    public Long getMemberId() {
        return memberId;
    }
}
