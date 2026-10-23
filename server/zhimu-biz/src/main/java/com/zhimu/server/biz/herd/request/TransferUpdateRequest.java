package com.zhimu.server.biz.herd.request;

import lombok.Data;

import java.time.LocalDate;

/** API-L22 修改转栏记录（L10 编辑模式）：去掉 batch_id、seen_stock_count，加 version。 */
@Data
public class TransferUpdateRequest {
    private Long fromPenId;
    private Long toPenId;
    private Integer quantity;
    private LocalDate eventDate;
    private String remark;
    private Boolean capacityConfirmed;
    private Integer version;
}
