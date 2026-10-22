package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-G12 保存饲喂方案（不要求幂等键，靠 version 防重）：本次点过“套用品种标准”时带 applied_std_version_id，撤销后清空；
 * abnormal_threshold 为 0.01–1.00；已有方案必带 version。
 */
public class FeedPlanSaveRequest {

    public Long appliedStdVersionId;
    public BigDecimal abnormalThreshold;
    public List<FeedPlanStageRequest> stages;
    public Integer version;
}
