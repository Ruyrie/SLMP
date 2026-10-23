package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 批次核算（t_batch_settlement，一个批次一行）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_settlement")
public class BatchSettlement extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private String status;
    private LocalDateTime settledAt;
    private BigDecimal saleIncome;
    private BigDecimal costFeed;
    private BigDecimal feedKg;
    private BigDecimal costIntro;
    private Integer introCount;
    private BigDecimal introUnitPrice;
    private BigDecimal costDrug;
    private BigDecimal costOther;
    private BigDecimal costTotal;
    private BigDecimal profit;
    private Integer soldCount;
    private BigDecimal costPerHead;
    private BigDecimal profitPerHead;
    private BigDecimal introWeightKg;
    private BigDecimal soldWeightKg;
    private BigDecimal netGainKg;
    private BigDecimal fcr;
    private LocalDateTime calcAt;

    @Version
    private Integer version;
}
