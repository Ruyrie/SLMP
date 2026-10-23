package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 出栏收款（t_sale_receipt）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_sale_receipt")
public class SaleReceipt extends AuditEntity {

    private Long farmId;
    private Long saleOrderId;
    private Long batchId;
    private BigDecimal amount;
    private LocalDate receiptDate;
    private String paymentMethod;
    private Long financeRecordId;
    private String status;
    private LocalDateTime voidedAt;
    private String clientRequestId;
}
