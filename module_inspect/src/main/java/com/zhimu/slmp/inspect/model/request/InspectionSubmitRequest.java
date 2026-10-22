package com.zhimu.slmp.inspect.model.request;

import java.util.List;

/**
 * API-I07 登记当天巡检（pen_id、client_request_id、location 必填，带 Idempotency-Key）/
 * API-I08 修改巡检（带 version，不带 pen_id、client_request_id、location 与幂等键）。
 * is_manual_marked 只在 can_manual_mark 时发送；manual_reason 开关开时发送；executor_id 只在 can_choose_executor 时发送。
 */
public class InspectionSubmitRequest {

    public Long penId;
    public String clientRequestId;
    public List<ItemResultRequest> itemResults;
    public Boolean isManualMarked;
    public String manualReason;
    public String remark;
    public Long executorId;
    public LocationRequest location;
    public List<Long> mediaFileIds;
    public Integer version;

    /** 一个巡检项目的结果 */
    public static class ItemResultRequest {
        public long itemId;
        public long optionId;

        public ItemResultRequest(long itemId, long optionId) {
            this.itemId = itemId;
            this.optionId = optionId;
        }
    }

    /** 登记位置：client_status 为 OK / DENIED / FAILED，非 OK 时坐标为 null；最多等 10 秒 */
    public static class LocationRequest {
        public static final String OK = "OK";
        public static final String DENIED = "DENIED";
        public static final String FAILED = "FAILED";

        public Double latitude;
        public Double longitude;
        public Double accuracyM;
        public String clientStatus;
    }
}
