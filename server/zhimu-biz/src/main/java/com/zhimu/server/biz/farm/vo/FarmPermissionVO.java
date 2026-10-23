package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 当前用户在当前养殖场的权限（15 项，F02/F03/F09–F12） */
@Data
public class FarmPermissionVO {
    private Boolean canViewFarmInfo;
    private Boolean canViewMembers;
    private Boolean canEditBasic;
    private Boolean canAdjustPens;
    private Boolean canSubmitChange;
    private Boolean canDisable;
    private Boolean canInvite;
    private Boolean canChangeRole;
    private Boolean canEnable;
    private Boolean canTransfer;
    private Boolean canRemoveMember;
    private Boolean canQuit;
    private Boolean canViewFinance;
    private Boolean canSubmitChangeRequest;
    private Boolean writable;
}
