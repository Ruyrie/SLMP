package com.zhimu.server.biz.herd.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** API-L23 死淘登记（L11）。pen_id 多栏舍时必填；executor_user_id 员工不传。 */
@Data
public class DeathCreateRequest {
    private Long batchId;
    private Long penId;
    private Integer quantity;
    private String reasonCode;
    private LocalDate eventDate;
    private Long executorUserId;
    private String remark;
    private List<Long> fileIds;
    private Integer seenStockCount;
}
