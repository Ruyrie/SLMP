package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 出栏单（t_sale_order）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_sale_order")
public class SaleOrder extends AuditEntity {

    private Long farmId;
    private Long batchId;
    private String saleNo;
    private Long penId;
    /** JSON 数组：[{pen_id, pen_name, quantity}] */
    private String penAllocations;
    private Long categoryId;
    private Long breedId;
    private LocalDate saleDate;
    private Integer quantity;
    private BigDecimal totalWeight;
    private BigDecimal avgWeight;
    private String pricingMethod;
    private BigDecimal unitPrice;
    private BigDecimal refUnitPrice;
    private BigDecimal saleAmount;
    private String buyerName;
    private LocalDate dueDate;
    private LocalDate effectiveDueDate;
    private BigDecimal receivedAmount;
    private BigDecimal unpaidAmount;
    private Integer receiptCount;
    private String paymentStatus;
    private LocalDate paidOffDate;
    private Long pendingChangeRequestId;
    private Boolean endedBatch;
    private Integer contentVersion;
    private String voidReason;
    private String voidSource;
    private LocalDateTime voidedAt;
    private Long voidedBy;
    private String clientRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
