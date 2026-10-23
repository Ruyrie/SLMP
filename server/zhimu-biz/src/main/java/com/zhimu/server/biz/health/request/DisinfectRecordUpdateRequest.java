package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** API-D40 修改消毒记录（类型和任务不可改）。 */
@Data
public class DisinfectRecordUpdateRequest {

    private LocalDateTime actualTime;
    private Long executorId;
    private Long drugLotId;
    private BigDecimal usageQty;
    private String concentration;
    private Boolean concentrationConfirmed;
    private String methodCode;
    private List<Long> fileIds = new ArrayList<>();
    private Integer version;
}
