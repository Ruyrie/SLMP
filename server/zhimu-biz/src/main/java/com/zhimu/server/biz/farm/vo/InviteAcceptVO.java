package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 同意邀请结果（F21） */
@Data
public class InviteAcceptVO {
    private Long farmId;
    private String farmName;
    private String role;
    private String roleText;
}
