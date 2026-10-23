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
    private List<PenBriefVO> unassignedPens;
    private Boolean showUnassignedBanner;
}
