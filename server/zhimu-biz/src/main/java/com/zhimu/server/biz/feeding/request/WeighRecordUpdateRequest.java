package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-G22 修改称重记录。 */
@Data
public class WeighRecordUpdateRequest {
    private String mode;
    private Integer sampleCount;
    private BigDecimal totalWeight;
    private List<WeighItemRequest> items;
    private Integer version;
}
