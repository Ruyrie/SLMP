package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.farm.vo.FarmPermissionVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;

/** 按角色与状态计算 15 项权限（模块02 文档 2.4 FarmPermissionVO 矩阵） */
public final class FarmPermissions {

    private FarmPermissions() {
    }

    public static FarmPermissionVO of(FarmStatus status, FarmRole role) {
        boolean owner = role == FarmRole.OWNER;
        boolean managerView = role == FarmRole.OWNER || role == FarmRole.MANAGER;
        boolean normal = status == FarmStatus.NORMAL;
        boolean disabled = status == FarmStatus.DISABLED;
        boolean readable = normal || disabled;
        boolean approved = normal || disabled || status == FarmStatus.FROZEN;

        FarmPermissionVO vo = new FarmPermissionVO();
        vo.setCanViewFarmInfo(managerView && readable);
        vo.setCanViewMembers(managerView && readable);
        vo.setCanEditBasic(managerView && normal);
        vo.setCanAdjustPens(managerView && normal);
        vo.setCanSubmitChange(owner && normal);
        vo.setCanDisable(owner && normal);
        vo.setCanInvite(owner && normal);
        vo.setCanChangeRole(owner && normal);
        vo.setCanEnable(owner && disabled);
        vo.setCanTransfer(owner && readable);
        vo.setCanRemoveMember(owner && readable);
        vo.setCanQuit(!owner && approved);
        vo.setCanViewFinance(managerView && readable);
        vo.setCanSubmitChangeRequest(managerView && normal);
        vo.setWritable(normal);
        return vo;
    }
}
