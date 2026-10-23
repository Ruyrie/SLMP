package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;

/** 逐头体重（WeighRecordCreateRequest.items）。 */
@Data
public class WeighItemRequest {
    private BigDecimal weight;
}
