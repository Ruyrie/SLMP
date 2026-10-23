package com.zhimu.server.biz.inspect.request;

import lombok.Data;

import java.util.List;

/** API-I09 补录漏巡：inspect_date 属于最近 3 天，executor_id、client_request_id 必填。 */
@Data
public class InspectionBackfillRequest {

    private Long penId;
    private String inspectDate;
    private Long executorId;
    private String clientRequestId;
    private List<InspectionSubmitRequest.ItemResultRequest> itemResults;
    private Boolean isManualMarked;
    private String manualReason;
    private String remark;
    private InspectionSubmitRequest.LocationRequest location;
    private List<Long> mediaFileIds;
}
