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

/** 收支流水（t_finance_record）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_finance_record")
public class FinanceRecord extends AuditEntity {

    private Long farmId;
    private String recordType;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long saleOrderId;
    private Long categoryId;
    private Long subcategoryId;
    private BigDecimal amount;
    private LocalDate recordDate;
    private Long batchId;
    private String note;
    private String detailText;
    private Integer voucherCount;
    private String status;
    private String voidSource;
    private LocalDateTime voidedAt;
    private Long voidedBy;
    private String clientRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
