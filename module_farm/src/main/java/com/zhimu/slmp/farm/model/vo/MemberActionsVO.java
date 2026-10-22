package com.zhimu.slmp.farm.model.vo;

/**
 * 成员详情可执行的操作（MemberDetailVO.actions；客户端暂定类名），按钮显隐只取这里的值。
 */
public class MemberActionsVO {

    private boolean canChangeRole;
    private boolean canAdjustPens;
    private boolean canRemove;
    private boolean canQuit;

    public boolean isCanChangeRole() {
        return canChangeRole;
    }

    public boolean isCanAdjustPens() {
        return canAdjustPens;
    }

    public boolean isCanRemove() {
        return canRemove;
    }

    public boolean isCanQuit() {
        return canQuit;
    }
}
