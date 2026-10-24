package com.zhimu.server.biz.farm.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.util.List;

/** 成员概况（F15、FarmDetailVO.member_summary） */
@Data
public class MemberSummaryVO {
    private Integer memberTotal;
    private Integer ownerCount;
    private Integer managerCount;
    private Integer staffCount;
    private Integer inviteCount;
    private Integer invitePendingCount;
    private List<String> avatars;
    /** 与 avatars 同序的头像临时地址，没有头像的成员为 null（Web 显示照片；App 仍按 avatars 显示首字） */
    private List<String> avatarUrls;
    private List<PenBriefVO> unassignedPens;
    private Boolean showUnassignedBanner;
}
