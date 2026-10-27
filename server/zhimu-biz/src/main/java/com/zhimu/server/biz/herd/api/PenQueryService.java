package com.zhimu.server.biz.herd.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 栏舍与存栏查询（模块04 实现，第 3 批）。在此之前由 biz.herd.support.TemporaryPenQueryService 提供示例数据。
 */
public interface PenQueryService {

    /** 养殖场的栏舍，按 pen_code 排序；includeDisabled 为 false 时只返回启用的 */
    List<PenInfo> listPens(Long farmId, boolean includeDisabled);

    /** 同场同品类的启用栏舍，供自繁后代选择入栏位置。 */
    List<PenInfo> listPensForCategory(Long farmId, Long categoryId);

    /** 按 ID 批量取栏舍（不限养殖场，调用方自行校验 farm_id） */
    Map<Long, PenInfo> getPens(Collection<Long> penIds);

    /** 各养殖场的当前总存栏（在养批次 current_count 之和）；没有存栏的养殖场返回 0 */
    Map<Long, Integer> farmCurrentStock(Collection<Long> farmIds);
}
