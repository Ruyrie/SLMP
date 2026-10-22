package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;
import java.util.List;

/**
 * 方案阶段（FeedPlanSaveRequest.stages）：新阶段不传 stage_id；日龄起 ≤ 止、前后连续、只有末段止可空；
 * 每头日投喂量大于 0、3 位小数、不超过 99.999；餐次 1–4，投喂时间 HH:mm 个数等于餐次且不重复。
 */
public class FeedPlanStageRequest {

    public Long stageId;
    public Long stdStageId;
    public String stageName;
    public int dayAgeStart;
    public Integer dayAgeEnd;
    public long feedId;
    public BigDecimal dailyQtyPerHead;
    public int mealsPerDay;
    public List<String> mealTimes;
}
