package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** API-D38 登记消毒。 */
@Data
public class DisinfectRecordCreateRequest {

    /** PLAN_TASK / EMPTY_PEN / EMERGENCY */
    private String recordType;
    private Long taskId;
    private Long penId;
    private LocalDateTime actualTime;
    private Long executorId;
    private Long drugId;
    private Long drugLotId;
    private BigDecimal usageQty;
    private String concentration;
    private Boolean concentrationConfirmed;
    private String methodCode;
    private List<Long> fileIds = new ArrayList<>();
}
