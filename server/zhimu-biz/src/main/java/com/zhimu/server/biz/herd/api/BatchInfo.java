package com.zhimu.server.biz.herd.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 批次简要信息（模块05–10 依赖）。
 *
 * @param farmId       养殖场 ID
 * @param batchId      批次 ID
 * @param batchNo      批次号，例如 ZM2610-031
 * @param breedId      品种 ID
 * @param categoryId   品类 ID
 * @param entryDate    入栏日期
 * @param currentCount 当前在栏数（= 各栏舍 t_batch_pen.stock_count 之和）
 * @param status       BATCH_STATUS：ACTIVE 在养 / ENDED 已结束
 * @param penIds       当前有在栏数的栏舍 ID（按栏舍 ID 升序）
 */
public record BatchInfo(Long farmId, Long batchId, String batchNo, Long breedId, Long categoryId,
                        LocalDate entryDate, Integer currentCount, String status, List<Long> penIds) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
