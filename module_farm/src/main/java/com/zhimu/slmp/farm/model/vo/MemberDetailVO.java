package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 成员详情（API-F17、F23 / F24 的 data.member）：MemberItemVO 全部字段 + phone（完整号码，拨号用）、farm_name、actions。
 */
public class MemberDetailVO extends MemberItemVO {

    @Nullable
    private String phone;
    @Nullable
    private String farmName;
    @Nullable
    private MemberActionsVO actions;

    @Nullable
    public String getPhone() {
        return phone;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @NonNull
    public MemberActionsVO getActions() {
        return actions == null ? new MemberActionsVO() : actions;
    }
}
