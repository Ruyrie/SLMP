package com.zhimu.server.biz.farm.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.util.List;

/** 修改角色、调整负责栏舍的结果（F23、F24） */
@Data
public class MemberChangeResultVO {
    private MemberDetailVO member;
    /** 操作前由该成员负责、操作后无其他员工负责的完整集合 */
    private List<PenBriefVO> newlyUnassignedPens;
}
