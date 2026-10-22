package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 邀请记录（API-F19、F20）。status：PENDING / ACCEPTED / REJECTED / EXPIRED / CANCELED；
 * invitee_phone 完整号码只给所有者（重新邀请预填）。
 */
public class InviteItemVO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private long inviteId;
    @Nullable
    private String inviteePhoneMasked;
    @Nullable
    private String inviteePhone;
    @Nullable
    private String roleText;
    @Nullable
    private String statusText;
    @Nullable
    private String invitedAt;
    @Nullable
    private String expiresAt;
    @Nullable
    private String handledAt;
    @Nullable
    private String role;
    @Nullable
    private String status;
    private boolean canReinvite;
    @Nullable
    private List<PenBriefVO> pens;

    public long getInviteId() {
        return inviteId;
    }

    @Nullable
    public String getInviteePhoneMasked() {
        return inviteePhoneMasked;
    }

    @Nullable
    public String getInviteePhone() {
        return inviteePhone;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getInvitedAt() {
        return invitedAt;
    }

    @Nullable
    public String getExpiresAt() {
        return expiresAt;
    }

    @Nullable
    public String getHandledAt() {
        return handledAt;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public boolean isCanReinvite() {
        return canReinvite;
    }

    @NonNull
    public List<PenBriefVO> getPens() {
        return pens == null ? Collections.emptyList() : pens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InviteItemVO)) {
            return false;
        }
        InviteItemVO that = (InviteItemVO) o;
        return inviteId == that.inviteId && canReinvite == that.canReinvite && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inviteId, status);
    }
}
