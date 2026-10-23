package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-G20 提交称重（请求头 Idempotency-Key）。 */
@Data
public class WeighRecordCreateRequest {
    private String weighDate;
    private String mode;
    private Integer sampleCount;
    private BigDecimal totalWeight;
    private List<WeighItemRequest> items;
}
