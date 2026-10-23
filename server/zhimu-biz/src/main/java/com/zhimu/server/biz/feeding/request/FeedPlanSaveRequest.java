package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-G12 保存饲喂方案。 */
@Data
public class FeedPlanSaveRequest {
    private Long appliedStdVersionId;
    private BigDecimal abnormalThreshold;
    private List<PlanStage> stages;
    private Integer version;
}
