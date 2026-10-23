package com.zhimu.server.biz.farm.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.util.List;

/** 邀请记录（F19、F20） */
@Data
public class InviteItemVO {
    private Long inviteId;
    private String inviteePhoneMasked;
    /** 完整号码只给所有者（重新邀请预填） */
    private String inviteePhone;
    private String role;
    private String roleText;
    private String status;
    private String statusText;
    private String invitedAt;
    private String expiresAt;
    private String handledAt;
    private Boolean canReinvite;
    private List<PenBriefVO> pens;
}
