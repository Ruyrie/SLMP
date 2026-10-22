package com.zhimu.slmp.inspect.model.request;

import java.util.List;

/**
 * API-I09 补录漏巡：inspect_date 属于 backfill_dates，executor_id 必填；带 Idempotency-Key（client_request_id）。
 */
public class InspectionBackfillRequest {

    public long penId;
    public String inspectDate;
    public long executorId;
    public String clientRequestId;
    public List<InspectionSubmitRequest.ItemResultRequest> itemResults;
    public Boolean isManualMarked;
    public String manualReason;
    public String remark;
    public InspectionSubmitRequest.LocationRequest location;
    public List<Long> mediaFileIds;
}
