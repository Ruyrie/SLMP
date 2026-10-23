package com.zhimu.server.biz.farm.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 成员详情（F17、F23 / F24 的 data.member） */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberDetailVO extends MemberItemVO {
    /** 完整号码，所有者、场长可见（App 拨号用） */
    private String phone;
    private String farmName;
    private MemberActionsVO actions;
}
