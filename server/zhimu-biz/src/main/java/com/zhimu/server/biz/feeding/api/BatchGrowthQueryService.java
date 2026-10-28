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

    /**
     * 最近一次称重与品种标准体重（出栏建议的“体重依据”用）。
     *
     * @param currentDayAge 批次当前日龄，用于取当前日龄的标准体重；可空
     */
    default WeighSnapshot latestWeighing(Long farmId, Long batchId, Long breedId, Integer currentDayAge) {
        return new WeighSnapshot(null, null, null, null, null, null);
    }

    /**
     * @param avgWeight         最近一次称重平均体重（千克），没有称重为 null
     * @param weighDate         称重日期
     * @param dayAge            称重时日龄
     * @param stdWeight         称重日龄的品种标准体重
     * @param deviationRatio    相对标准的偏离（小数，-0.04 表示低 4%）
     * @param currentStdWeight  当前日龄的品种标准体重
     */
    record WeighSnapshot(BigDecimal avgWeight, LocalDate weighDate, Integer dayAge, BigDecimal stdWeight,
                         BigDecimal deviationRatio, BigDecimal currentStdWeight) {
    }
}
