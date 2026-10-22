package com.zhimu.slmp.farm.model.request;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 发送邀请 / 重新邀请（API-F20，要求 Idempotency-Key）：pen_ids 只在 STAFF 时传（可 0 个）；resend_of_id 重新邀请时传。
 */
public class InviteMemberRequest {

    private final String inviteePhone;
    private final String role;
    @Nullable
    private final List<Long> penIds;
    @Nullable
    private final Long resendOfId;

    public InviteMemberRequest(String inviteePhone, String role, @Nullable List<Long> penIds,
                               @Nullable Long resendOfId) {
        this.inviteePhone = inviteePhone;
        this.role = role;
        this.penIds = penIds == null ? null : new ArrayList<>(penIds);
        this.resendOfId = resendOfId;
    }

    public String getInviteePhone() {
        return inviteePhone;
    }

    public String getRole() {
        return role;
    }

    @Nullable
    public List<Long> getPenIds() {
        return penIds;
    }

    @Nullable
    public Long getResendOfId() {
        return resendOfId;
    }
}
