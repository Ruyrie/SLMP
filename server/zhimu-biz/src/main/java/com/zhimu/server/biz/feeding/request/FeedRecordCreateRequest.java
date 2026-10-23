package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;

/** API-G04 确认投喂（请求头 Idempotency-Key）。 */
@Data
public class FeedRecordCreateRequest {
    private Long taskId;
    private Long feedId;
    private BigDecimal actualQty;
    private Long executorId;
    private String remark;
}
