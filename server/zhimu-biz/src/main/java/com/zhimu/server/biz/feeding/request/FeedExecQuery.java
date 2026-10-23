package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.time.LocalDate;

/** G04 执行情况查询：batch_id 与 all_batches 二选一。 */
@Data
public class FeedExecQuery {
    private Long batchId;
    private Boolean allBatches;
    private String range;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate date;
}
