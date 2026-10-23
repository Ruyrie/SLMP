package com.zhimu.server.biz.inspect.request;

import lombok.Data;

import java.util.List;

/**
 * API-I07 登记当天巡检 / API-I08 修改巡检。
 * 登记时 pen_id、client_request_id、location 必填；修改时 version 必填，其余同登记。
 */
@Data
public class InspectionSubmitRequest {

    private Long penId;
    private String clientRequestId;
    private List<ItemResultRequest> itemResults;
    private Boolean isManualMarked;
    private String manualReason;
    private String remark;
    private Long executorId;
    private LocationRequest location;
    private List<Long> mediaFileIds;
    private Integer version;

    @Data
    public static class ItemResultRequest {
        private Long itemId;
        private Long optionId;
    }

    @Data
    public static class LocationRequest {
        private Double latitude;
        private Double longitude;
        private Double accuracyM;
        /** OK / DENIED / FAILED */
        private String clientStatus;
    }
}
