package com.zhimu.slmp.inspect.model.request;

import java.util.List;

/**
 * API-I16 异常上报（I05）：client_request_id 兼作 Idempotency-Key；症状与描述至少满足其一（无症状时描述 ≥ 10 字）。
 */
public class AbnormalReportCreateRequest {

    public String clientRequestId;
    public long penId;
    public long batchId;
    public Long inspectionId;
    public int sickCount;
    public List<Long> symptomIds;
    public String description;
    public List<Long> mediaFileIds;
}
