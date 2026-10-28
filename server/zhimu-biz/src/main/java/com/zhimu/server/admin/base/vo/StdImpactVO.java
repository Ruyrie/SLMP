package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 发布影响预估（ADM-D10）：按 breed_id 统计在养批次、分布养殖场、已到改动日龄段的批次、套用方案数。 */
@Data
public class StdImpactVO {

    /** 在养批次（t_batch ACTIVE） */
    private Integer activeBatchCount;
    /** 在养批次分布的养殖场数（去重） */
    private Integer farmCount;
    /** 当前日龄已到最早改动日龄的在养批次 */
    private Integer affectedBatchCount;
    private List<FlagChangeVO> flagChanges = new ArrayList<>();
    /** 在养批次中套用过该品种饲喂标准的方案数（仅饲喂标准） */
    private Integer appliedPlanCount;
}
