package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 成员详情可执行的操作（MemberDetailVO.actions） */
@Data
public class MemberActionsVO {
    private Boolean canChangeRole;
    private Boolean canAdjustPens;
    private Boolean canRemove;
    private Boolean canQuit;
}
