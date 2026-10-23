package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 盘点单（t_stocktake，模块09 2.3.7）：只盘饲料。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stocktake")
public class Stocktake extends AuditEntity {

    private Long farmId;
    private String stocktakeNo;
    private LocalDate stocktakeDate;
    private Integer itemCount;
    private Integer gainCount;
    private Integer lossCount;
    private BigDecimal gainAmount;
    private BigDecimal lossAmount;
    private String clientRequestId;
    @Version
    private Integer version;
    private Long updatedBy;
}
