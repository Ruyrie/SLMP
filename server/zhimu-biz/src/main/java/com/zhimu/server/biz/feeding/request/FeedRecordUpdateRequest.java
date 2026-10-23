package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;

/** API-G06 修改当天投喂记录。 */
@Data
public class FeedRecordUpdateRequest {
    private Long feedId;
    private BigDecimal actualQty;
    private Long executorId;
    private String remark;
    private Integer version;
}
