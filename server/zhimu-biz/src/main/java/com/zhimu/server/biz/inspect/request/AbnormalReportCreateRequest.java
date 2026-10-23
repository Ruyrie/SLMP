package com.zhimu.server.biz.inspect.request;

import lombok.Data;

import java.util.List;

/** API-I16 异常上报：client_request_id 兼作 Idempotency-Key。 */
@Data
public class AbnormalReportCreateRequest {

    private String clientRequestId;
    private Long penId;
    private Long batchId;
    private Long inspectionId;
    private Integer sickCount;
    private List<Long> symptomIds;
    private String description;
    private List<Long> mediaFileIds;
}
