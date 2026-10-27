package com.zhimu.server.biz.feeding.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 跨模块只读生长数据；调用方先校验批次权限，查询始终限定养殖场。 */
public interface BatchGrowthQueryService {

    Map<Long, LatestWeight> latestWeights(Long farmId, Collection<Long> batchIds);

    record LatestWeight(BigDecimal avgWeight, LocalDate weighDate) {
    }
}
