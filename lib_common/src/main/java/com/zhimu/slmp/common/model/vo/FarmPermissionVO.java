package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 当前用户在当前养殖场的权限（15 项），按钮与入口显隐只取这里的值。
 */
public class FarmPermissionVO {

    private boolean canViewFarmInfo;
    private boolean canViewMembers;
    private boolean canEditBasic;
    private boolean canAdjustPens;
    private boolean canSubmitChange;
    private boolean canDisable;
    private boolean canInvite;
    private boolean canChangeRole;
    private boolean canEnable;
    private boolean canTransfer;
    private boolean canRemoveMember;
    private boolean canQuit;
    private boolean canViewFinance;
    private boolean canSubmitChangeRequest;
    private boolean writable;

    public boolean isCanViewFarmInfo() {
        return canViewFarmInfo;
    }

    public boolean isCanViewMembers() {
        return canViewMembers;
    }

    public boolean isCanEditBasic() {
        return canEditBasic;
    }

    public boolean isCanAdjustPens() {
        return canAdjustPens;
    }

    public boolean isCanSubmitChange() {
        return canSubmitChange;
    }

    public boolean isCanDisable() {
        return canDisable;
    }

    public boolean isCanInvite() {
        return canInvite;
    }

    public boolean isCanChangeRole() {
        return canChangeRole;
    }

    public boolean isCanEnable() {
        return canEnable;
    }

    public boolean isCanTransfer() {
        return canTransfer;
    }

    public boolean isCanRemoveMember() {
        return canRemoveMember;
    }

    public boolean isCanQuit() {
        return canQuit;
    }

    public boolean isCanViewFinance() {
        return canViewFinance;
    }

    public boolean isCanSubmitChangeRequest() {
        return canSubmitChangeRequest;
    }

    public boolean isWritable() {
        return writable;
    }
}
