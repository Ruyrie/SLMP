package com.zhimu.server.biz.stock.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 实际到账登记/当日更正，不涉及库存数量或库存成本。 */
@Data
public class StockReturnRefundRequest {
    private BigDecimal amount;
    private LocalDate refundDate;
    private String remark;
    private Integer version;
}
