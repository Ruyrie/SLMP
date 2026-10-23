package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 库存流水（t_stock_flow，模块09 2.3.9）：唯一记账事实，数量与来源写入后不改。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stock_flow")
public class StockFlow extends BaseEntity {

    private Long farmId;
    /** FEED / DRUG */
    private String materialType;
    private Long feedId;
    private Long drugId;
    private Long drugLotId;
    private String flowType;
    /** IN / OUT */
    private String direction;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal stockQtyAfter;
    private BigDecimal stockAmountAfter;
    private String bizType;
    private Long bizId;
    private Long batchId;
    private Long penId;
    private Long operatorId;
    private String relatedDesc;
    private LocalDateTime occurAt;
    private LocalDate bizDate;
    private Boolean isBatchCost;
    private Boolean isFeedUsage;
    /** 记账顺序键：首次写入等于自身 ID，更正后的新流水沿用原值 */
    private Long originFlowId;
    private String status;
    private String voidReason;
    private LocalDateTime voidAt;
    private LocalDateTime recalcAt;
}
