package com.zhimu.server.admin.base.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 标准体重查询（模块06/07 与 U12 使用）。
 * 由 admin.base 实现；不直接暴露 Mapper。
 */
public interface StdQueryService {

    /** API-U12：当前生效（PUBLISHED）的标准体重与饲喂标准 */
    com.zhimu.server.admin.base.vo.BreedStandardsVO standards(Long breedId, Integer dayAge);

    /**
     * 按品种和日龄解析生长阶段（取当前生效饲喂标准）；没有标准或超出全部阶段返回 null。
     * 供模块04 批次标题、模块06 饲喂方案使用。
     */
    StageInfo resolveStage(Long breedId, Integer dayAge);

    /** 品种当前生效标准体重版本 ID；没有返回 null（模块06 称重记录 std_version_id） */
    Long currentWeightVersionId(Long breedId);

    /** 品种当前生效饲喂标准版本 ID；没有返回 null */
    Long currentFeedVersionId(Long breedId);

    /** 模块06 重算完成后回写 recalculated_at；版本不存在返回 false */
    boolean markRecalculated(Long weightVersionId);

    /** 生长阶段快照 */
    record StageInfo(Long versionId, String stageName, String stageShortName, int dayAgeStart,
                     Integer dayAgeEnd, List<String> mealTimes, BigDecimal dailyFeed, Integer mealsPerDay) {
    }
}
