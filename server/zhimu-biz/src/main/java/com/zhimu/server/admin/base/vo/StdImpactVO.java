package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 发布影响预估（ADM-D10）。统计类字段待模块04/06，本批返回 0 / 空数组而不是 null。 */
@Data
public class StdImpactVO {

    /** 在养批次，待模块04 */
    private Integer activeBatchCount;
    /** 分布养殖场，待模块04 */
    private Integer farmCount;
    /** 当前日龄已到改动日龄段的批次，待模块06 */
    private Integer affectedBatchCount;
    private List<FlagChangeVO> flagChanges = new ArrayList<>();
    /** 套用方案数，待模块06（feed） */
    private Integer appliedPlanCount;
}
