package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报损与报废（t_stock_loss，模块09 2.3.6）：报损只针对饲料，报废只针对药品批号。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stock_loss")
public class StockLoss extends AuditEntity {

    private Long farmId;
    /** FEED_LOSS / DRUG_SCRAP */
    private String lossType;
    private Long feedId;
    private Long drugId;
    private Long drugLotId;
    private BigDecimal inputQty;
    private String inputUnit;
    private BigDecimal qty;
    private String reason;
    private String remark;
    private LocalDate lossDate;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private Long flowId;
    private String status;
    private String clientRequestId;
    @Version
    private Integer version;
    private Long updatedBy;
}
