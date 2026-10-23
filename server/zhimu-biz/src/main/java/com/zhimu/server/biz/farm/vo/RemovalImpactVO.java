package com.zhimu.server.biz.farm.vo;

import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.util.List;

/** 移出影响（F25） */
@Data
public class RemovalImpactVO {
    private Long memberId;
    private String memberName;
    private String role;
    private Integer pendingWorkTodoCount;
    private List<PenBriefVO> orphanPens;
}
