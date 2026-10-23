package com.zhimu.server.biz.farm.request;

import lombok.Data;

import java.util.List;

/** 发送邀请 / 重新邀请（API-F20） */
@Data
public class InviteMemberRequest {
    private String inviteePhone;
    /** MANAGER / STAFF */
    private String role;
    private List<Long> penIds;
    private Long resendOfId;
}
