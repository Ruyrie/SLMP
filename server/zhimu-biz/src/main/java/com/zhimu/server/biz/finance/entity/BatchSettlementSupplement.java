package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批次补录待确认（t_batch_settlement_supplement）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_settlement_supplement")
public class BatchSettlementSupplement extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Long settlementId;
    private String sourceType;
    private Long sourceId;
    private String changeAction;
    private LocalDate bizDate;
    private String title;
    private String costGroup;
    private BigDecimal amount;
    private BigDecimal qtyDelta;
    private Long changeRequestId;
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String cancelReason;
}
