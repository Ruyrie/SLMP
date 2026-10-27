package com.zhimu.server.biz.herd.api;

import java.time.LocalDate;

/**
 * 自繁批次的入栏数量与存栏调整（模块05 产仔 / 孵化的修改申请审核通过时调用，模块04 实现）。
 * <p>
 * 对应的技术口径见模块05 文档 2.9.1：活仔 / 健雏差额调整批次 entry_count 与 current_count，
 * 并同步入栏栏舍的在栏数与 t_batch_pen；调整后存栏为负时返回 42205。
 */
public interface SelfBredBatchService {

    /** 与繁育登记共用事务：创建自繁商品批次并关联来源记录。 */
    CreatedBatch create(Long penId, Long breedId, int quantity, LocalDate date,
                        boolean capacityConfirmed, String sourceType, Long sourceId);

    record CreatedBatch(Long batchId, String batchNo) {}

    /**
     * 按差额调整自繁批次。
     *
     * @param batchId    自繁批次 ID
     * @param countDelta 入栏数与当前存栏的调整量（正负均可，0 表示只改日期）
     * @param entryDate  新的入栏日期；为 null 表示不改
     * @return 调整后的当前存栏
     */
    int adjustEntry(Long batchId, int countDelta, LocalDate entryDate);
}
