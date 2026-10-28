package com.zhimu.server.biz.herd.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 批次查询（模块04 实现，模块05–10 使用）。只读，不校验当前养殖场上下文；调用方自行判断返回的 farmId。
 */
public interface BatchQueryService {

    /** 按 ID 取批次；不存在或已删除抛 40401 */
    BatchInfo requireBatch(Long batchId);

    /** 核算所需的入栏原始数据；调用方必须先校验养殖场。 */
    AccountingBasis accountingBasis(Long batchId);

    record AccountingBasis(BigDecimal introUnitPrice, BigDecimal entryAvgWeight, LocalDateTime endedAt) { }

    /** 养殖场全部在养批次（status = ACTIVE），按入栏日期升序、批次 ID 升序 */
    List<BatchInfo> listActive(Long farmId);

    /** 批次存在、未删除且在养 */
    boolean isActive(Long batchId);

    /** 批次各栏舍当前在栏数（t_batch_pen，只含在栏数大于 0 的栏舍，按栏舍 ID 升序）；调用方自行校验养殖场 */
    default List<PenStock> penStocks(Long batchId) {
        return List.of();
    }

    record PenStock(Long penId, int stockCount) { }

    /** 养殖场在日期区间内（含两端）登记的死淘头数（生效的 DEATH 事件之和，经营报表死亡率用），默认 0 */
    default int deathCount(Collection<Long> farmIds, LocalDate start, LocalDate end) {
        return 0;
    }

    /** 指定日期末的存栏：以当前存栏反推日期之后的生效入栏、出栏和死淘，不使用另一统计期近似。 */
    default int stockCountAt(Collection<Long> farmIds, LocalDate date) {
        return 0;
    }
}
