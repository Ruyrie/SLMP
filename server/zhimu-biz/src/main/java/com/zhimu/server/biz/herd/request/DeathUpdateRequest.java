package com.zhimu.server.biz.herd.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** API-L24 修改死淘记录（L11 编辑模式）：去掉 batch_id、seen_stock_count，加 version。 */
@Data
public class DeathUpdateRequest {
    private Long penId;
    private Integer quantity;
    private String reasonCode;
    private LocalDate eventDate;
    private Long executorUserId;
    private String remark;
    private List<Long> fileIds;
    private Integer version;
}
