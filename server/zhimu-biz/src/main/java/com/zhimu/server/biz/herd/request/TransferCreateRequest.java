package com.zhimu.server.biz.herd.request;

import lombok.Data;

import java.time.LocalDate;

/** API-L21 转栏登记（L10）。seen_stock_count 用于选择 42205 文案。 */
@Data
public class TransferCreateRequest {
    private Long batchId;
    private Long fromPenId;
    private Long toPenId;
    private Integer quantity;
    private LocalDate eventDate;
    private String remark;
    private Boolean capacityConfirmed;
    private Integer seenStockCount;
}
