package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 药品批号结存（t_drug_lot，模块09 2.3.3）：每个“药品 + 批号”单独结存与均价。 */
@Getter
@Setter
@FarmScoped
@TableName("t_drug_lot")
public class DrugLot extends BaseEntity {

    private Long farmId;
    private Long drugId;
    private Long catalogId;
    private String category;
    private String lotNo;
    private LocalDate expireDate;
    private LocalDate firstInboundDate;
    private Integer inboundCount;
    private BigDecimal stockQty;
    private BigDecimal stockAmount;
    private BigDecimal avgPrice;
    private Long recallId;
    private LocalDateTime recalledAt;
    private String recallReason;
    private BigDecimal recallStockQty;
    private LocalDateTime nearNotifiedAt;
    private LocalDateTime expiredNotifiedAt;
    @Version
    private Integer version;
}
