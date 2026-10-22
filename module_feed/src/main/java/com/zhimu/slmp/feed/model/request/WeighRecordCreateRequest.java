package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-G20 提交称重（请求头 Idempotency-Key）：入栏日期 ≤ 称重日期 ≤ 今天；只提交当前录入方式的字段
 * （SAMPLE：sample_count、total_weight；INDIVIDUAL：items）。
 */
public class WeighRecordCreateRequest {

    public String weighDate;
    public String mode;
    public Integer sampleCount;
    public BigDecimal totalWeight;
    public List<WeighItemRequest> items;
}
