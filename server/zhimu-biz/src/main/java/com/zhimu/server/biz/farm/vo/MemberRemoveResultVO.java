package com.zhimu.server.biz.farm.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.util.List;

/** 移出成员结果（F26） */
@Data
public class MemberRemoveResultVO {
    private List<PenBriefVO> unassignedPens;
}
