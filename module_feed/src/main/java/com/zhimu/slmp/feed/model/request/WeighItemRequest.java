package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;

/** 逐头录入的一头（按顺序编号）：大于 0、1 位小数、不超过 9999.9。 */
public class WeighItemRequest {

    public BigDecimal weight;

    public WeighItemRequest() {
    }

    public WeighItemRequest(BigDecimal weight) {
        this.weight = weight;
    }
}
