package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 成员概况（API-F15、FarmDetailVO.member_summary）。
 */
public class MemberSummaryVO {

    private int memberTotal;
    private int ownerCount;
    private int managerCount;
    private int staffCount;
    private int inviteCount;
    private int invitePendingCount;
    @Nullable
    private List<String> avatars;
    @Nullable
    private List<PenBriefVO> unassignedPens;
    private boolean showUnassignedBanner;

    public int getMemberTotal() {
        return memberTotal;
    }

    public int getOwnerCount() {
        return ownerCount;
    }

    public int getManagerCount() {
        return managerCount;
    }

    public int getStaffCount() {
        return staffCount;
    }

    public int getInviteCount() {
        return inviteCount;
    }

    public int getInvitePendingCount() {
        return invitePendingCount;
    }

    @NonNull
    public List<String> getAvatars() {
        return avatars == null ? Collections.emptyList() : avatars;
    }

    @NonNull
    public List<PenBriefVO> getUnassignedPens() {
        return unassignedPens == null ? Collections.emptyList() : unassignedPens;
    }

    public boolean isShowUnassignedBanner() {
        return showUnassignedBanner;
    }
}
